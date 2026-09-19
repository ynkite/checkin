package idusw.sbb.checkin.domain.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한 방향 정렬(작업지시 3번) — 티맵이 준 최적 순서를 하루에 끼워 넣는 규칙.
 *
 * <p>티맵 최적 순서를 그대로 쓰면 12:00 에 카페가, 13:30 에 맛집이 오는 날이 생긴다.
 * 자리(=시각)는 그대로 두고 <b>같은 type 끼리만</b> 바꾼다. 되돌아가는 구간은 대개
 * 「동쪽 식당이 점심, 서쪽 식당이 저녁」에서 생기므로 이 자유도면 충분하다.
 *
 * <p>티맵 응답 자체는 지금 막혀 있어(아래 회신 참고) 실제 생성으로는 확인이 안 된다.
 * 순서를 받았다고 치고 끼워 넣는 규칙만 여기서 지킨다.
 */
class RouteOneWayOrderTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void 같은_성격끼리만_자리를_바꾼다() {
        // 자리: [tour, food, cafe, food] · 티맵 최적 순서는 식당 둘이 뒤집힌 것
        var slots = List.of(p("해운대해수욕장", "tour"), p("동쪽식당", "food"),
                            p("카페A", "cafe"), p("서쪽식당", "food"));
        var optimized = List.of(p("해운대해수욕장", "tour"), p("서쪽식당", "food"),
                                p("카페A", "cafe"), p("동쪽식당", "food"));

        var out = AiRouteService.assignByType(slots, optimized, Set.of());

        // 점심 자리에는 식당이, 카페 자리에는 카페가 그대로 온다
        assertThat(types(out)).containsExactly("tour", "food", "cafe", "food");
        // 다만 어느 식당이 점심인지는 뒤집힌다 — 되돌아가는 구간이 이걸로 풀린다
        assertThat(names(out)).containsExactly("해운대해수욕장", "서쪽식당", "카페A", "동쪽식당");
    }

    @Test
    void 사용자가_요청한_장소는_자리를_지킨다() {
        var slots = List.of(p("동쪽식당", "food"), p("꼭가야하는집", "food"), p("카페A", "cafe"));
        var optimized = List.of(p("카페A", "cafe"), p("동쪽식당", "food"));

        var out = AiRouteService.assignByType(slots, optimized, Set.of("꼭가야하는집"));

        assertThat(names(out).get(1)).isEqualTo("꼭가야하는집");
    }

    @Test
    void 성격이_하나뿐이면_순서가_그대로다() {
        var slots = List.of(p("A", "tour"), p("B", "food"));
        var optimized = List.of(p("A", "tour"), p("B", "food"));

        var out = AiRouteService.assignByType(slots, optimized, Set.of());

        assertThat(names(out)).containsExactly("A", "B");
    }

    @Test
    void 같은_성격이_동나면_순서대로_채운다() {
        // 자리에는 food 가 둘인데 최적 순서에 food 가 하나뿐인 어긋남 — 빈 자리를 남기지 않는다
        var slots = List.of(p("식당1", "food"), p("식당2", "food"));
        var optimized = List.of(p("식당1", "food"), p("전망대", "tour"));

        var out = AiRouteService.assignByType(slots, optimized, Set.of());

        assertThat(out).hasSize(2);
        assertThat(names(out)).containsExactlyInAnyOrder("식당1", "전망대");
    }

    @Test
    void 장소를_잃어버리지_않는다() {
        var slots = List.of(p("A", "tour"), p("B", "food"), p("C", "cafe"));
        var optimized = List.of(p("C", "cafe"), p("A", "tour"), p("B", "food"));

        var out = AiRouteService.assignByType(slots, optimized, Set.of());

        assertThat(out).hasSize(3);
        assertThat(names(out)).containsExactlyInAnyOrder("A", "B", "C");
    }

    /* ── 바닥 ──────────────────────────────────────────────── */

    private static ObjectNode p(String name, String type) {
        ObjectNode o = OM.createObjectNode();
        o.put("name", name);
        o.put("type", type);
        return o;
    }

    private static List<String> names(List<ObjectNode> l) {
        return l.stream().map(n -> n.path("name").asText()).toList();
    }

    private static List<String> types(List<ObjectNode> l) {
        return l.stream().map(n -> n.path("type").asText()).toList();
    }
}
