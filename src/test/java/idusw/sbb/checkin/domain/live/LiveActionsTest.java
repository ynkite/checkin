package idusw.sbb.checkin.domain.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.crowd.CrowdService;
import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.tour.service.TourAreaInfoService;
import idusw.sbb.checkin.domain.weather.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실시간 화면에 무슨 갈래가 올라오는지 본다.
 *
 * <p><b>왜 이 테스트가 있나</b> — 배포 주소에서 심사 계정으로 실시간을 열었더니
 * 단추가 「길 안내」와 「전체 일정」 둘뿐이었다. 우리가 제일 내세우는
 * 「순서 바꾸기」와 「다른 곳으로」가 안 보였다. 원인이 둘이었다.
 *
 * <ol>
 *   <li>여행지가 「부산」이라 AreaCode 가 첫 시군구(중구)를 골랐다. 일정은
 *       동구·사하구·중구·수영구·해운대구를 오가는데 전부 중구에 물어서
 *       자갈치시장 말고는 하나도 안 맞았다</li>
 *   <li>그걸 고쳐도 그날 부산은 52~62 라 70 문턱을 못 넘었다.
 *       어제는 73~95 였다. 한적한 날에 열면 기능이 화면에서 사라진다</li>
 * </ol>
 *
 * <p>문턱을 낮추지는 않았다. 그건 숫자를 화면에 맞추는 짓이다.
 * 늘 보여 주되 지금 권하는 것인지를 {@code suggested} 로 가른다.
 */
class LiveActionsTest {

    /** 오늘 하루, 아침 일찍부터 저녁까지. 좌표는 해운대다 */
    private static final String ROUTE = """
            [{"day":1,"places":[
              {"name":"해운대 해수욕장","time":"09:00","lat":35.15852,"lng":129.15985},
              {"name":"광안리 해수욕장","time":"14:00","lat":35.15319,"lng":129.11898}
            ]}]""";

    private final TravelPlanRepository planRepository = mock(TravelPlanRepository.class);
    private final CrowdService crowdService = mock(CrowdService.class);
    private final TourAreaInfoService tourAreaInfoService = mock(TourAreaInfoService.class);
    private final LiveService live = new LiveService(
            planRepository, crowdService, tourAreaInfoService,
            mock(WeatherService.class), new ObjectMapper());

    private void 여행하나(Integer rate) {
        TravelPlan p = TravelPlan.builder()
                .title("부산 2박 3일").destination("부산")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(p, "id", 1L);
        ReflectionTestUtils.setField(p, "status", "FIXED");
        ReflectionTestUtils.setField(p, "routeJson", ROUTE);
        when(planRepository.findById(1L)).thenReturn(Optional.of(p));

        /* 좌표로 해운대구를 잡아 준다. 여행지 이름만 보면 중구가 나온다 */
        when(tourAreaInfoService.resolveArea(anyString(), any(), any()))
                .thenReturn(AreaCode.find("부산 해운대구"));
        when(crowdService.forecast(anyString(), anyString(), anyString(), any()))
                .thenReturn(rate == null ? null
                        : new CrowdForecast("해운대해수욕장", "20260921", rate.doubleValue(),
                                "mid", "정상", "TOUR", "부산광역시", "해운대구", null));
    }

    private List<Map<String, Object>> 갈래() {
        return live.snapshot(1L, null, null, LocalDate.now()).actions();
    }

    private Map<String, Object> 갈래(String key) {
        return 갈래().stream().filter(a -> key.equals(a.get("key"))).findFirst().orElse(null);
    }

    @Test
    void 한적해도_일정을_다시_짤_수_있다() {
        여행하나(58);   /* 배포에서 실제로 나온 값대 */

        assertThat(갈래().stream().map(a -> a.get("key")))
                .contains("swap", "quiet");
    }

    @Test
    void 한적하면_권하지는_않는다() {
        여행하나(58);

        assertThat(갈래("swap").get("suggested")).isEqualTo(false);
        /* 한적한데 「붐비는 곳을 뒤로」라고 쓰면 거짓말이다 */
        assertThat(String.valueOf(갈래("swap").get("note"))).doesNotContain("붐비는");
    }

    @Test
    void 붐비면_권한다() {
        여행하나(88);

        assertThat(갈래("swap").get("suggested")).isEqualTo(true);
        assertThat(String.valueOf(갈래("swap").get("note"))).contains("붐비는");
        assertThat(String.valueOf(갈래("quiet").get("note"))).contains("한적한");
    }

    @Test
    void 정거장_좌표로_시군구를_잡는다() {
        여행하나(88);

        /* 여행지 이름은 「부산」이다. 그것만 보면 중구가 나와 해운대가 안 맞는다 */
        assertThat(live.snapshot(1L, null, null, LocalDate.now()).crowd())
                .containsEntry("rate", 88L);
    }

    @Test
    void 혼잡도를_못_받아도_갈래는_남는다() {
        여행하나(null);   /* 집중률을 못 받은 경우 */

        assertThat(갈래().stream().map(a -> a.get("key")))
                .contains("swap", "quiet");
        assertThat(갈래("swap").get("suggested")).isEqualTo(false);
    }
}
