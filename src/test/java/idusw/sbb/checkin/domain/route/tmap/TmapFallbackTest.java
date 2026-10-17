package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.route.tmap.dto.NaviRoute;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelLeg;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelPlan;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelPlanRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 티맵이 한도 초과 · 시간 초과일 때 화면이 빈칸 대신 예상값을 받는지. 실제 티맵은 부르지 않는다. */
class TmapFallbackTest {

    private static final String ROUTE = "{\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\","
            + "\"coordinates\":[129.04,35.11]},\"properties\":{\"totalTime\":600,\"totalDistance\":4000}}]}";

    private final RestTemplate rt = mock(RestTemplate.class);

    private TmapClient client(String keys) {
        TmapClient c = new TmapClient(rt, new ObjectMapper());
        ReflectionTestUtils.invokeMethod(c, "setKey", keys);
        ReflectionTestUtils.setField(c, "baseUrl", "http://tmap.test");
        return c;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private org.mockito.stubbing.OngoingStubbing<ResponseEntity<String>> onExchange() {
        return when(rt.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)));
    }

    @Test
    void 같은_요청은_한_번만_부른다() {
        onExchange().thenReturn(ResponseEntity.ok(ROUTE));
        TmapClient c = client("k1");

        assertThat(c.carRoute(129.04, 35.11, 129.16, 35.16, "부산역", "해운대", null)).isNotNull();
        assertThat(c.carRoute(129.04, 35.11, 129.16, 35.16, "부산역", "해운대", null)).isNotNull();

        verify(rt, times(1)).exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void 시간_초과면_잠시_부르지_않는다() {
        onExchange().thenThrow(new ResourceAccessException("Read timed out"));
        TmapClient c = client("k1");

        assertThat(c.carRoute(129.04, 35.11, 129.16, 35.16, "a", "b", null)).isNull();
        assertThat(c.carRoute(129.00, 35.00, 129.10, 35.10, "c", "d", null)).isNull();

        verify(rt, times(1)).exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void 마지막_키까지_한도가_차면_예상_시간으로_잰다() {
        onExchange().thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "quota", null, null, null));
        TravelTimeService svc = new TravelTimeService(client("k1,k2"));

        TravelPlan p = svc.plan(new TravelPlanRequest("부산역", 35.115, 129.041, "CAR", null,
                List.of(new TravelPlanRequest.Stop("해운대", 35.1587, 129.1604, null),
                        new TravelPlanRequest.Stop("광안리", 35.1532, 129.1186, null))));

        assertThat(p.ready()).isTrue();
        assertThat(p.note()).isEqualTo(RouteEstimate.NOTE);
        assertThat(p.legs()).allMatch(TravelLeg::estimated);
        assertThat(p.legs()).allMatch(l -> l.minutes() != null && l.minutes() > 0 && "CAR".equals(l.mode()));
        assertThat(p.totalMinutes()).isPositive();
        // 두 키 모두 429 를 받은 뒤로는 부르지 않는다
        verify(rt, times(2)).exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void 키가_없어도_좌표가_있으면_예상값을_준다() {
        TravelTimeService svc = new TravelTimeService(client(""));
        TravelPlan p = svc.plan(new TravelPlanRequest("부산역", 35.115, 129.041, "CAR", null,
                List.of(new TravelPlanRequest.Stop("해운대", 35.1587, 129.1604, null))));

        assertThat(p.legs().get(0).estimated()).isTrue();
        verifyNoInteractions(rt);
    }

    @Test
    void 네비는_답이_없으면_직선_예상_경로를_준다() {
        TmapClient tmap = mock(TmapClient.class);
        when(tmap.carRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any())).thenReturn(null);
        NaviService navi = new NaviService(tmap, mock(SafetySeed.class));

        NaviRoute r = navi.driveRoute(129.041, 35.115, 129.1604, 35.1587, "현재 위치", "해운대");

        assertThat(r.ready()).isTrue();
        assertThat(r.line()).hasSize(2);
        assertThat(r.line().get(0)).containsExactly(35.115, 129.041);
        assertThat(r.totalSeconds()).isPositive();
        assertThat(r.alertSource()).isEqualTo("예상");
        assertThat(r.note()).isEqualTo(RouteEstimate.NOTE);
    }
}
