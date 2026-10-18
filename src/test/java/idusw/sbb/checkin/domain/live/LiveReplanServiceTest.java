package idusw.sbb.checkin.domain.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.route.dto.RouteDelta;
import idusw.sbb.checkin.domain.route.service.AiRouteService;
import idusw.sbb.checkin.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 「순서 바꾸기」를 묻는 창에 시간·돈 변화를 먼저 보여 준다.
 * 미리 보기는 저장하면 안 되고, 못 잰 변화는 지어내지 않는다.
 */
class LiveReplanServiceTest {

    private static final String BEFORE = "[{\"places\":["
            + "{\"name\":\"붐비는 곳\",\"crowd\":90,\"time\":\"10:00\"},"
            + "{\"transit\":\"🚗 자차 · 약 20분 · ₩1,000\"},"
            + "{\"name\":\"한적한 곳\",\"crowd\":10,\"time\":\"12:00\"}]}]";
    private static final String AFTER = BEFORE.replace("약 20분 · ₩1,000", "약 30분 · ₩2,000");

    private TravelPlanRepository plans;
    private AiRouteService routes;
    private LiveReplanService svc;

    @BeforeEach
    void setUp() {
        plans = mock(TravelPlanRepository.class);
        routes = mock(AiRouteService.class);
        svc = new LiveReplanService(plans, routes, new ObjectMapper());

        User u = mock(User.class);
        when(u.getId()).thenReturn(7L);
        when(plans.findById(1L)).thenReturn(Optional.of(TravelPlan.builder().user(u).routeJson(BEFORE).build()));
    }

    @Test
    void 미리_보기는_저장하지_않고_변화만_낸다() {
        when(routes.previewEnriched(anyLong(), anyString())).thenReturn(AFTER);

        Map<String, Object> r = svc.swapCrowded(1L, 1, 7L, true);

        assertThat(r.get("changed")).isEqualTo(true);
        assertThat(r.get("delta")).isEqualTo(new RouteDelta(10, 1000L));
        verify(routes, never()).saveAiRouteToDb(anyLong(), anyString());
    }

    @Test
    void 바꾸면_저장하고_같은_변화를_싣는다() {
        when(routes.previewEnriched(anyLong(), anyString())).thenReturn(AFTER);

        Map<String, Object> r = svc.swapCrowded(1L, 1, 7L, false);

        assertThat(r.get("delta")).isEqualTo(new RouteDelta(10, 1000L));
        verify(routes).saveAiRouteToDb(eq(1L), anyString());
    }

    @Test
    void 못_잰_구간이_있으면_변화를_싣지_않는다() {
        when(routes.previewEnriched(anyLong(), anyString())).thenReturn(BEFORE.replace("약 20분 · ₩1,000", "이동"));

        assertThat(svc.swapCrowded(1L, 1, 7L, true)).doesNotContainKey("delta");
    }
}
