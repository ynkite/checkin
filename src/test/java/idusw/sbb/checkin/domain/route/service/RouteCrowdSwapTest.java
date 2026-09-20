package idusw.sbb.checkin.domain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 붐비는 곳을 한적한 곳으로 갈아 끼우는 규칙 (작업지시 4번).
 *
 * <p>프롬프트에 「90 이상은 피하라」를 적어도 지켜지지 않았다. 실제로 장산(95)이 그대로
 * 동선에 들어왔다. 성격 중복 때와 같다 — 부탁이 아니라 코드여야 한다.
 *
 * <p>여기서 지키는 것은 <b>빼지 않고 바꾼다</b>는 것이다. 부산·경주에서 90 이상을 다 빼면
 * 갈 곳이 없다.
 */
class RouteCrowdSwapTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void 붐비는_곳을_한적한_곳으로_바꾼다() {
        JsonNode route = day(place("장산", "tour", 95));
        var pool = pool(cand("장산", "tour", 95), cand("문탠로드", "tour", 30));

        AiRouteService.swapCrowdedPlaces(route, byName(pool), pool);

        assertThat(names(route)).containsExactly("문탠로드");
        assertThat(crowdOf(route, 0)).isEqualTo(30);
    }

    @Test
    void 갈아_낄_곳이_없으면_그대로_둔다() {
        // 90 이상뿐이면 빼서 하루를 비우지 않는다
        JsonNode route = day(place("장산", "tour", 95));
        var pool = pool(cand("장산", "tour", 95), cand("해운대해수욕장", "tour", 97));

        AiRouteService.swapCrowdedPlaces(route, byName(pool), pool);

        assertThat(names(route)).containsExactly("장산");
    }

    @Test
    void 붐비지_않으면_건드리지_않는다() {
        JsonNode route = day(place("송정해수욕장", "tour", 62));
        var pool = pool(cand("송정해수욕장", "tour", 62), cand("문탠로드", "tour", 10));

        AiRouteService.swapCrowdedPlaces(route, byName(pool), pool);

        assertThat(names(route)).containsExactly("송정해수욕장");
    }

    @Test
    void 집중률을_모르는_곳도_대체_대상이다() {
        JsonNode route = day(place("장산", "tour", 95));
        var pool = pool(cand("장산", "tour", 95), candNoCrowd("해리단길", "tour"));

        AiRouteService.swapCrowdedPlaces(route, byName(pool), pool);

        assertThat(names(route)).containsExactly("해리단길");
        // 모르는 것을 「한적하다」고 적지 않는다 — crowd 필드가 붙지 않아야 한다
        assertThat(first(route).has("crowd")).isFalse();
    }

    @Test
    void 성격이_다른_후보로는_바꾸지_않는다() {
        JsonNode route = day(place("장산", "tour", 95));
        var pool = pool(cand("장산", "tour", 95), cand("어느식당", "food", 10));

        AiRouteService.swapCrowdedPlaces(route, byName(pool), pool);

        assertThat(names(route)).containsExactly("장산");
    }

    @Test
    void 숙소는_바꾸지_않는다() {
        // 하루가 숙소 주변으로 모이므로 여기서 바꾸면 여행이 통째로 딸려 간다
        JsonNode route = day(place("붐비는호텔", "stay", 99));
        var pool = pool(cand("붐비는호텔", "stay", 99), cand("한적한호텔", "stay", 10));

        AiRouteService.swapCrowdedPlaces(route, byName(pool), pool);

        assertThat(names(route)).containsExactly("붐비는호텔");
    }

    /* ── 바닥 ──────────────────────────────────────────────── */

    private static JsonNode day(ObjectNode... places) {
        ArrayNode arr = OM.createArrayNode();
        for (ObjectNode p : places) arr.add(p);
        ObjectNode d = OM.createObjectNode();
        d.set("places", arr);
        ArrayNode route = OM.createArrayNode();
        route.add(d);
        return route;
    }

    private static ObjectNode place(String name, String type, int crowd) {
        ObjectNode o = OM.createObjectNode();
        o.put("name", name);
        o.put("type", type);
        o.put("crowd", crowd);
        return o;
    }

    private static ObjectNode cand(String name, String type, int crowd) {
        ObjectNode o = place(name, type, crowd);
        o.put("sub", "관광지 · 1h · ₩0×2");
        return o;
    }

    private static ObjectNode candNoCrowd(String name, String type) {
        ObjectNode o = OM.createObjectNode();
        o.put("name", name);
        o.put("type", type);
        o.put("sub", "관광지 · 1h · ₩0×2");
        return o;
    }

    private static Map<String, List<ObjectNode>> pool(ObjectNode... cands) {
        Map<String, List<ObjectNode>> m = new java.util.LinkedHashMap<>();
        for (ObjectNode c : cands)
            m.computeIfAbsent(c.path("type").asText(), k -> new java.util.ArrayList<>()).add(c);
        return m;
    }

    private static Map<String, ObjectNode> byName(Map<String, List<ObjectNode>> pool) {
        Map<String, ObjectNode> m = new java.util.LinkedHashMap<>();
        pool.values().forEach(l -> l.forEach(n -> m.putIfAbsent(n.path("name").asText(), n)));
        return m;
    }

    private static List<String> names(JsonNode route) {
        List<String> out = new java.util.ArrayList<>();
        for (JsonNode d : route)
            for (JsonNode p : d.path("places"))
                if (!p.has("transit")) out.add(p.path("name").asText());
        return out;
    }

    private static JsonNode first(JsonNode route) {
        return route.get(0).path("places").get(0);
    }

    private static int crowdOf(JsonNode route, int i) {
        return route.get(0).path("places").get(i).path("crowd").asInt();
    }
}
