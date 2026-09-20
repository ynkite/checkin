package idusw.sbb.checkin.domain.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 밀도 상한을 누가 먼저 가져가는지.
 *
 * <p>전에는 먼 장소가 상한 판정을 통째로 건너뛰었다(continue). 그래서 「여유롭게」(관광지 1개)인데
 * 관광지가 2곳이 되고, 가까운 운촌당산·동백섬은 밀도초과로 잘렸다 — 거꾸로다.
 * 멀리까지 가기로 한 곳을 두고 가까운 곳을 남기면 사용자가 요청한 의미가 없다.
 *
 * <p>이제 먼 장소도 상한을 세되, <b>먼저 자리를 잡는다.</b> 줄일 것은 가까운 쪽이다.
 */
class RouteFarPriorityTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static ObjectNode p(String name, String type) {
        ObjectNode o = OM.createObjectNode();
        o.put("name", name);
        o.put("type", type);
        return o;
    }

    @Test
    void 숙소가_가장_먼저다() {
        // 숙소는 자리 자체가 고정이다
        assertThat(AiRouteService.priority(p("호텔", "stay"), Set.of(), false)).isEqualTo(3);
    }

    @Test
    void 사용자가_요청한_곳이_그_다음이다() {
        int req = AiRouteService.priority(p("통도사", "tour"), Set.of("통도사"), false);
        int far = AiRouteService.priority(p("먼절", "tour"), Set.of(), true);

        assertThat(req).isGreaterThan(far);
    }

    @Test
    void 먼_곳이_가까운_곳보다_먼저다() {
        int far  = AiRouteService.priority(p("먼절", "tour"), Set.of(), true);
        int near = AiRouteService.priority(p("가까운관광", "tour"), Set.of(), false);

        assertThat(far).isGreaterThan(near);
    }

    @Test
    void 사용자_요청이면서_먼_곳은_요청_쪽으로_센다() {
        // 둘 다 해당해도 등급이 두 번 오르지는 않는다
        assertThat(AiRouteService.priority(p("통도사", "tour"), Set.of("통도사"), true)).isEqualTo(2);
    }

    @Test
    void 그냥_가까운_곳이_가장_나중이다() {
        assertThat(AiRouteService.priority(p("카페", "cafe"), Set.of(), false)).isZero();
    }
}
