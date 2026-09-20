package idusw.sbb.checkin.domain.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.crowd.CrowdService;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.weather.service.WeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실시간 화면의 여행 목록에 무엇이 올라오는지 본다.
 *
 * <p>배포 주소에서 실제로 겪은 일이다 — 만들다 만 일정(DRAFT)이 실시간 목록에는
 * 떠 있는데 「내 여행」에서는 안 보였다. 「내 여행」은 FIXED 만 보여 주기 때문이다.
 * 사용자 입장에서는 없앨 방법이 없는 항목이 하나 생긴 셈이다.
 * 두 화면이 같은 여행을 두고 다른 말을 하면 화면을 못 믿게 된다.
 */
class LiveOpenableTripsTest {

    private static final String ROUTE = """
            [{"day":1,"places":[{"name":"자갈치 시장","lat":35.09,"lng":129.03,"time":"09:00"}]}]""";

    private final TravelPlanRepository planRepository = mock(TravelPlanRepository.class);
    private final LiveService live = new LiveService(
            planRepository, mock(CrowdService.class),
            mock(idusw.sbb.checkin.domain.tour.service.TourAreaInfoService.class),
            mock(WeatherService.class), new ObjectMapper());

    private TravelPlan plan(long id, String title, String status, String route) {
        TravelPlan p = TravelPlan.builder()
                .title(title).destination("부산")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "status", status);
        ReflectionTestUtils.setField(p, "routeJson", route);
        return p;
    }

    private void 로그인(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(userId, "ROLE_USER"), null, List.of()));
    }

    @AfterEach
    void 정리() {
        SecurityContextHolder.clearContext();
    }

    private List<String> 제목들() {
        return live.openableTrips().stream().map(m -> String.valueOf(m.get("title"))).toList();
    }

    @Test
    void 확정한_여행만_올라온다() {
        로그인(7L);
        when(planRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                plan(1L, "부산 2박 3일", "FIXED", ROUTE),
                plan(2L, "만들다 만 대전", "DRAFT", ROUTE)));

        assertThat(제목들()).containsExactly("부산 2박 3일");
    }

    @Test
    void 초대받아_저장한_것은_경로가_없어서_안_올라온다() {
        로그인(7L);
        when(planRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(
                plan(3L, "친구가 보낸 링크", "INVITED", null)));

        assertThat(제목들()).isEmpty();
    }

    @Test
    void 로그인하지_않으면_빈_목록이다() {
        /* 남의 여행이 섞여 나오면 안 된다. 없는 게 맞다 */
        assertThat(live.openableTrips()).isEmpty();
    }

    @Test
    void 끝난_지_이틀_넘은_여행은_빠진다() {
        로그인(7L);
        TravelPlan 옛것 = plan(4L, "지난달 경주", "FIXED", ROUTE);
        ReflectionTestUtils.setField(옛것, "startDate", LocalDate.now().minusDays(10));
        ReflectionTestUtils.setField(옛것, "endDate", LocalDate.now().minusDays(9));
        when(planRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(옛것));

        assertThat(제목들()).isEmpty();
    }

    @Test
    void 여행_전날에도_열_수_있다() {
        로그인(7L);
        TravelPlan 내일 = plan(5L, "내일 떠나는 강릉", "FIXED", ROUTE);
        ReflectionTestUtils.setField(내일, "startDate", LocalDate.now().plusDays(1));
        ReflectionTestUtils.setField(내일, "endDate", LocalDate.now().plusDays(2));
        when(planRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(내일));

        Map<String, Object> m = live.openableTrips().get(0);
        assertThat(m.get("title")).isEqualTo("내일 떠나는 강릉");
        assertThat(m.get("phase")).isEqualTo("BEFORE");
    }
}
