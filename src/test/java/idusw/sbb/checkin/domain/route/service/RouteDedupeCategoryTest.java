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
 * 「같은 날 같은 성격 두 번 금지」가 실제로 갈아 끼우는지 본다 (작업지시 2번).
 *
 * <p>실제 생성으로는 확인이 안 됐다 — 하루에 tour 가 한두 곳뿐이라 성격이 겹칠 일이
 * 드물었고, 관광공사에서 넣은 조건 후보에는 업종 문자열이 아예 없다. 그래서 겹치는 날을
 * 직접 만들어 확인한다.
 *
 * <p>여기서 지키는 것은 <b>지우지 않고 바꾼다</b>는 것이다. 지우면 하루가 빈다 —
 * 엔진 경로에서 3일에 7곳만 남고 11시에 일정이 끝나는 꼴을 봤다.
 */
class RouteDedupeCategoryTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void 같은_날_해변이_두_번이면_다른_성격으로_갈아_끼운다() {
        JsonNode route = oneDay("해운대해수욕장", "송정해수욕장");
        var candidates = pool(
                cand("해운대해수욕장", "여행 > 관광,명소 > 해수욕장,해변"),
                cand("송정해수욕장",   "여행 > 관광,명소 > 해수욕장,해변"),
                cand("부산엑스더스카이", "여행 > 관광,명소 > 전망대"));

        AiRouteService.dedupeDayCategories(route, byName(candidates), candidates);

        assertThat(names(route)).containsExactly("해운대해수욕장", "부산엑스더스카이");
    }

    @Test
    void 갈아_낄_후보가_없으면_지우지_않고_둔다() {
        JsonNode route = oneDay("해운대해수욕장", "송정해수욕장");
        var candidates = pool(
                cand("해운대해수욕장", "여행 > 관광,명소 > 해수욕장,해변"),
                cand("송정해수욕장",   "여행 > 관광,명소 > 해수욕장,해변"));

        AiRouteService.dedupeDayCategories(route, byName(candidates), candidates);

        // 하루를 비우는 것보다 겹치는 편이 낫다
        assertThat(names(route)).containsExactly("해운대해수욕장", "송정해수욕장");
    }

    @Test
    void 성격이_다르면_건드리지_않는다() {
        JsonNode route = oneDay("해운대해수욕장", "부산엑스더스카이");
        var candidates = pool(
                cand("해운대해수욕장",   "여행 > 관광,명소 > 해수욕장,해변"),
                cand("부산엑스더스카이", "여행 > 관광,명소 > 전망대"),
                cand("해운대수목원",     "여행 > 관광,명소 > 수목원,식물원"));

        AiRouteService.dedupeDayCategories(route, byName(candidates), candidates);

        assertThat(names(route)).containsExactly("해운대해수욕장", "부산엑스더스카이");
    }

    @Test
    void 업종을_모르는_곳끼리는_겹친다고_보지_않는다() {
        // 관광공사에서 넣은 조건 후보에는 업종이 없다. 그걸 한 덩어리로 묶으면 멀쩡한 곳이 교체된다
        JsonNode route = oneDay("더펫텔프리미엄스위트", "캔버스 블랙");
        var candidates = pool(
                cand("더펫텔프리미엄스위트", ""),
                cand("캔버스 블랙", ""),
                cand("부산엑스더스카이", "여행 > 관광,명소 > 전망대"));

        AiRouteService.dedupeDayCategories(route, byName(candidates), candidates);

        assertThat(names(route)).containsExactly("더펫텔프리미엄스위트", "캔버스 블랙");
    }

    @Test
    void 끼니는_하루에_여러_번이_정상이다() {
        ArrayNode places = OM.createArrayNode();
        places.add(place("점심집", "food"));
        places.add(place("저녁집", "food"));
        ArrayNode route = OM.createArrayNode();
        ObjectNode day = OM.createObjectNode();
        day.set("places", places);
        route.add(day);

        var candidates = pool(
                cand("점심집", "음식점 > 한식"),
                cand("저녁집", "음식점 > 한식"),
                cand("부산엑스더스카이", "여행 > 관광,명소 > 전망대"));

        AiRouteService.dedupeDayCategories(route, byName(candidates), candidates);

        assertThat(names(route)).containsExactly("점심집", "저녁집");
    }

    /* ── 바닥 ──────────────────────────────────────────────── */

    private static JsonNode oneDay(String... tourNames) {
        ArrayNode places = OM.createArrayNode();
        for (String n : tourNames) places.add(place(n, "tour"));
        ObjectNode day = OM.createObjectNode();
        day.set("places", places);
        ArrayNode route = OM.createArrayNode();
        route.add(day);
        return route;
    }

    private static ObjectNode place(String name, String type) {
        ObjectNode o = OM.createObjectNode();
        o.put("name", name);
        o.put("type", type);
        return o;
    }

    private static ObjectNode cand(String name, String category) {
        ObjectNode o = OM.createObjectNode();
        o.put("name", name);
        o.put("category", category);
        o.put("sub", "관광지 · 1h · ₩0×2");
        return o;
    }

    private static Map<String, List<ObjectNode>> pool(ObjectNode... cands) {
        return Map.of("tour", List.of(cands));
    }

    private static Map<String, ObjectNode> byName(Map<String, List<ObjectNode>> pool) {
        return pool.get("tour").stream()
                .collect(java.util.stream.Collectors.toMap(n -> n.path("name").asText(), n -> n, (a, b) -> a));
    }

    private static List<String> names(JsonNode route) {
        List<String> out = new java.util.ArrayList<>();
        for (JsonNode day : route)
            for (JsonNode p : day.path("places"))
                if (!p.has("transit")) out.add(p.path("name").asText());
        return out;
    }
}
