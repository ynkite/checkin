package idusw.sbb.checkin.domain.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 같은 외부 호출을 두 번 하지 않는다 — 길찾기 한 구간, 같은 동선의 연속 저장. */
class RouteCallDedupeTest {

    private final RestTemplate rest = mock(RestTemplate.class);

    private AiRouteService service() {
        return new AiRouteService(
                mock(idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository.class),
                mock(idusw.sbb.checkin.domain.expense.repository.ExpenseRepository.class),
                mock(idusw.sbb.checkin.domain.place.service.PlaceService.class),
                mock(idusw.sbb.checkin.domain.crowd.CrowdService.class),
                mock(idusw.sbb.checkin.domain.weather.service.WeatherService.class),
                mock(idusw.sbb.checkin.domain.route.tmap.TravelTimeService.class),
                mock(idusw.sbb.checkin.domain.place.repository.PlaceRepository.class),
                mock(idusw.sbb.checkin.domain.tour.service.TourAreaInfoService.class),
                mock(idusw.sbb.checkin.domain.route.tmap.NaviService.class),
                new ObjectMapper(), rest,
                mock(ChatModel.class), mock(ChatModel.class), mock(ChatModel.class));
    }

    private static final String ROUTE_BODY = """
        {"routes":[{"summary":{"distance":3200,"duration":540,"fare":{"toll":0}},
          "sections":[{"roads":[{"vertexes":[129.10,35.10,129.11,35.11,129.12,35.12]}]}]}]}
        """;

    @Test
    void 거리와_도로좌표는_길찾기_한_번으로_같이_받는다() {
        when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(ROUTE_BODY));
        AiRouteService svc = service();
        double[] a = {35.10, 129.10}, b = {35.12, 129.12};

        assertArrayEquals(new long[]{3200, 540, 0}, svc.carDirections(a, b));
        var path = svc.carPath(a, b);
        assertEquals(3, path.size());
        assertArrayEquals(new double[]{35.11, 129.11}, path.get(1));   // [위도, 경도]

        verify(rest, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void 길찾기가_실패하면_전처럼_null_이고_다음에_다시_묻는다() {
        when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("카카오 장애"));
        AiRouteService svc = service();
        double[] a = {35.10, 129.10}, b = {35.12, 129.12};

        assertNull(svc.carDirections(a, b));
        assertNull(svc.carPath(a, b));
        verify(rest, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void 같은_동선을_잇달아_저장하면_보정은_한_번만_돈다() {
        AiRouteService svc = spy(service());
        TravelPlan plan = mock(TravelPlan.class);
        doAnswer(inv -> inv.getArgument(1) + "+보정").when(svc).enrich(any(), anyString());

        assertEquals("A+보정", svc.enrichOnce(1L, plan, "A"));
        assertEquals("A+보정", svc.enrichOnce(1L, plan, "A"));   // 컨트롤러의 두 번째 저장
        verify(svc, times(1)).enrich(any(), eq("A"));

        assertEquals("B+보정", svc.enrichOnce(1L, plan, "B"));   // 입력이 바뀌면 다시 보정
        assertEquals("A+보정", svc.enrichOnce(2L, plan, "A"));   // 다른 여행은 따로
        verify(svc, times(3)).enrich(any(), anyString());
    }

    @Test
    void 구간_양끝_좌표() throws Exception {
        var places = new ObjectMapper().readTree("""
            [{"name":"가","lat":35.1,"lng":129.1},{"transit":"이동"},{"name":"나","lat":35.2,"lng":129.2},
             {"transit":"이동"},{"name":"좌표없음"}]
            """);
        double[][] ends = AiRouteService.legEnds(places, 1);
        assertArrayEquals(new double[]{35.1, 129.1}, ends[0]);
        assertArrayEquals(new double[]{35.2, 129.2}, ends[1]);
        assertNull(AiRouteService.legEnds(places, 0), "장소 자리");
        assertNull(AiRouteService.legEnds(places, 3), "뒤 장소에 좌표가 없다");
    }
}
