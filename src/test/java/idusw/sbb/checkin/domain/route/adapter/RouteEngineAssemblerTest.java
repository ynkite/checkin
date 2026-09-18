package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteEngineAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 강릉 경포권 — 스텁과 같은 좌표대를 쓴다. */
    private static final double BASE_LAT = 37.8058;
    private static final double BASE_LNG = 128.8968;

    private final RouteEngineAssembler assembler = new RouteEngineAssembler();

    private static ObjectNode node(String name, double northKm, String sub) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", name);
        node.put("lat", BASE_LAT + northKm / 111.194);
        node.put("lng", BASE_LNG);
        node.put("sub", sub);
        node.put("stars", "평점 정보 없음");
        return node;
    }

    /** collectCandidatesFromKakao 출력 모양 — stay 1 · food 6 · cafe 3 · tour 12. */
    private static Map<String, List<ObjectNode>> kakaoCandidates() {
        Map<String, List<ObjectNode>> byType = new LinkedHashMap<>();
        byType.put("stay", List.of(node("경포비치호텔", 0, "숙소 · ₩120,000")));

        List<ObjectNode> food = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            food.add(node("식당" + k, 1 + k * 0.4, "맛집 · 점심 · ₩10,000×4"));
        }
        byType.put("food", food);

        List<ObjectNode> cafe = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            cafe.add(node("카페" + k, 1.2 + k * 0.5, "카페 · ₩6,500×4"));
        }
        byType.put("cafe", cafe);

        List<ObjectNode> tour = new ArrayList<>();
        for (int k = 0; k < 12; k++) {
            tour.add(node("관광지" + k, 0.8 + k * 1.5, "관광지 · 1h · ₩0×4"));
        }
        byType.put("tour", tour);
        return byType;
    }

    private static TravelPlan plan(LocalDate start, LocalDate end) {
        return TravelPlan.builder().id(27L).destination("강릉").startDate(start).endDate(end).build();
    }

    private static PlanInputForm form(String transportType, String extraNotes) {
        return PlanInputForm.builder()
                .departure("서울")
                .transportType(transportType)
                .scheduleDensity("빼곡하게")
                .companionCount(4)
                .extraNotes(extraNotes)
                .build();
    }

    private JsonNode assemble(TravelPlan plan, PlanInputForm form, Set<String> userRequested) throws Exception {
        return MAPPER.readTree(assembler.assemble(plan, form, kakaoCandidates(), userRequested));
    }

    private static List<JsonNode> placesOf(JsonNode dayNode) {
        List<JsonNode> places = new ArrayList<>();
        for (JsonNode place : dayNode.path("places")) {
            if (!place.has("transit")) {
                places.add(place);
            }
        }
        return places;
    }

    private static List<String> typesOf(JsonNode dayNode) {
        return placesOf(dayNode).stream().map(p -> p.path("type").asText()).toList();
    }

    // ── 기본 구조 ────────────────────────────────────────────────────────

    @Test
    void 이박삼일이면_일차_셋이_나온다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), Set.of());

        assertThat(root.isArray()).isTrue();
        assertThat(root).hasSize(3);
        assertThat(root.get(0).path("day").asInt()).isEqualTo(1);
        assertThat(root.get(2).path("day").asInt()).isEqualTo(3);
        assertThat(root.get(0).path("label").asText()).isEqualTo("📅 Day 1 · 09/19 (토)");
    }

    @Test
    void 숙소는_첫날_끝_중간날_양끝_마지막날_시작에_붙는다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), Set.of());

        assertThat(typesOf(root.get(0))).last().isEqualTo("stay");
        assertThat(typesOf(root.get(0))).first().isNotEqualTo("stay");
        assertThat(typesOf(root.get(1))).first().isEqualTo("stay");
        assertThat(typesOf(root.get(1))).last().isEqualTo("stay");
        assertThat(typesOf(root.get(2))).first().isEqualTo("stay");
        assertThat(typesOf(root.get(2))).last().isNotEqualTo("stay");
    }

    @Test
    void 당일치기는_숙소_행이_없다() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 19);
        JsonNode root = assemble(plan(day, day), form("자차", null), Set.of());

        assertThat(root).hasSize(1);
        assertThat(typesOf(root.get(0))).doesNotContain("stay");
        assertThat(placesOf(root.get(0))).isNotEmpty();
    }

    // ── 결정 4 : 첫날 도착 시각 ──────────────────────────────────────────

    @Test
    void 첫날은_교통수단_기본_도착시각_이후에_시작한다() throws Exception {
        JsonNode car = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), Set.of());
        JsonNode transit = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("대중교통", null), Set.of());

        assertThat(firstPlaceTime(car)).isAfterOrEqualTo(LocalTime.of(11, 0));
        assertThat(firstPlaceTime(transit)).isAfterOrEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void extraNotes_의_첫날도착_태그가_기본값을_덮어쓴다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", "[EXTRA:첫날도착:15:00]"), Set.of());

        assertThat(firstPlaceTime(root)).isAfterOrEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void label_value_형태로_저장된_첫날도착도_읽는다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", "[{\"label\":\"첫날도착\",\"value\":\"15:00\"}]"), Set.of());

        assertThat(firstPlaceTime(root)).isAfterOrEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void 못_읽는_시각은_무시하고_기본값으로_간다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", "[EXTRA:첫날도착:아무때나]"), Set.of());

        assertThat(firstPlaceTime(root)).isAfterOrEqualTo(LocalTime.of(11, 0));
    }

    private static LocalTime firstPlaceTime(JsonNode root) {
        return LocalTime.parse(placesOf(root.get(0)).get(0).path("time").asText());
    }

    // ── 결정 13 : 사용자 요청 장소는 반드시 들어간다 ─────────────────────

    @Test
    void 사용자_요청_장소는_일정에_들어간다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), Set.of("관광지11"));

        List<String> names = new ArrayList<>();
        for (JsonNode day : root) {
            placesOf(day).forEach(p -> names.add(p.path("name").asText()));
        }
        assertThat(names).contains("관광지11");
    }

    // ── 원본 통과 · 결정론 ───────────────────────────────────────────────

    @Test
    void sub_와_stars_는_카카오_후보_값_그대로다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), Set.of());

        JsonNode stay = placesOf(root.get(1)).get(0);
        assertThat(stay.path("sub").asText()).isEqualTo("숙소 · ₩120,000");
        assertThat(placesOf(root.get(0))).allSatisfy(place ->
                assertThat(place.path("stars").asText()).isEqualTo("평점 정보 없음"));
    }

    @Test
    void 뒤에서_심는_필드는_비워_둔다() throws Exception {
        JsonNode root = assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), Set.of());

        for (JsonNode day : root) {
            for (JsonNode place : day.path("places")) {
                assertThat(place.has("isFound")).isFalse();
                assertThat(place.has("pathCoords")).isFalse();
                assertThat(place.has("crowd")).isFalse();
            }
        }
    }

    @Test
    void 같은_입력이면_같은_JSON이_나온다() {
        TravelPlan plan = plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21));
        PlanInputForm form = form("자차", null);

        String first = assembler.assemble(plan, form, kakaoCandidates(), Set.of());
        String second = assembler.assemble(plan, form, kakaoCandidates(), Set.of());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void 후보가_없으면_예외로_알린다() {
        // 폴백 분기를 태우기 위한 신호다 — 빈 일정을 조용히 내보내지 않는다
        assertThatThrownBy(() -> assembler.assemble(
                plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), Map.of(), Set.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── 결정 14 : 저녁이 굶지 않는다 ─────────────────────────────────────

    /** 강릉 실제 좌표 — FOOD 6 / TOUR 10 / CAFE 3. 진단에 썼던 그 입력이다. */
    private static Map<String, List<ObjectNode>> gangneungCandidates() {
        Map<String, List<ObjectNode>> byType = new LinkedHashMap<>();
        byType.put("stay", List.of(at("경포비치호텔", 37.8058, 128.8968, "숙소 · ₩120,000")));
        byType.put("food", List.of(
                at("초당순두부마을", 37.7956, 128.9075, "맛집 · 점심 · ₩10,000×4"),
                at("강릉중앙시장", 37.7556, 128.8961, "맛집 · 점심 · ₩12,000×4"),
                at("주문진해변회센터", 37.8935, 128.8290, "맛집 · 저녁 · ₩35,000×4"),
                at("진부시장", 37.6470, 128.5570, "맛집 · 점심 · ₩9,000×4"),
                at("횡계황태마을", 37.6700, 128.7100, "맛집 · 저녁 · ₩15,000×4"),
                at("안목커피거리회집", 37.7730, 128.9470, "맛집 · 저녁 · ₩30,000×4")));
        byType.put("cafe", List.of(
                at("경포호수카페거리", 37.7930, 128.8960, "카페 · ₩6,500×4"),
                at("안목커피거리", 37.7735, 128.9475, "카페 · ₩6,000×4"),
                at("대관령카페", 37.6800, 128.7300, "카페 · ₩5,500×4")));
        byType.put("tour", List.of(
                at("경포대", 37.7960, 128.8960, "관광지 · 1h · ₩0×4"),
                at("경포해변", 37.8030, 128.9100, "관광지 · 1h · ₩0×4"),
                at("강문해변", 37.7890, 128.9180, "관광지 · 1h · ₩0×4"),
                at("오죽헌", 37.7790, 128.8780, "관광지 · 1h · ₩3,000×4"),
                at("정동진", 37.6900, 129.0340, "관광지 · 1h · ₩0×4"),
                at("월정사", 37.7310, 128.5930, "관광지 · 1h · ₩5,000×4"),
                at("소금강", 37.7700, 128.6800, "관광지 · 1h · ₩0×4"),
                at("대관령양떼목장", 37.6900, 128.7300, "관광지 · 1h · ₩7,000×4"),
                at("주문진해변", 37.8930, 128.8290, "관광지 · 1h · ₩0×4"),
                at("안반데기", 37.6230, 128.7480, "관광지 · 1h · ₩0×4")));
        return byType;
    }

    private static ObjectNode at(String name, double lat, double lng, String sub) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", name);
        node.put("lat", lat);
        node.put("lng", lng);
        node.put("sub", sub);
        node.put("stars", "평점 정보 없음");
        return node;
    }

    @Test
    void 강릉_사흘_전부_점심과_저녁이_한_곳씩_들어간다() throws Exception {
        String json = assembler.assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), gangneungCandidates(), Set.of());
        JsonNode root = MAPPER.readTree(json);

        assertThat(root).hasSize(3);
        for (JsonNode day : root) {
            List<JsonNode> meals = placesOf(day).stream()
                    .filter(p -> p.path("type").asText().equals("food"))
                    .toList();
            assertThat(meals).as(day.path("label").asText() + " 식사").hasSize(2);

            LocalTime lunch = LocalTime.parse(meals.get(0).path("time").asText());
            LocalTime dinner = LocalTime.parse(meals.get(1).path("time").asText());
            assertThat(lunch).isBetween(LocalTime.of(12, 0), LocalTime.of(13, 30));
            assertThat(dinner).isBetween(LocalTime.of(18, 0), LocalTime.of(19, 30));
            assertThat(meals.get(0).path("name").asText()).isNotEqualTo(meals.get(1).path("name").asText());
        }
    }

    @Test
    void 같은_장소가_하루에_두_번_나오지_않는다() throws Exception {
        String json = assembler.assemble(plan(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 21)),
                form("자차", null), gangneungCandidates(), Set.of());

        for (JsonNode day : MAPPER.readTree(json)) {
            List<String> names = placesOf(day).stream()
                    .filter(p -> !p.path("type").asText().equals("stay"))
                    .map(p -> p.path("name").asText())
                    .toList();
            assertThat(names).as(day.path("label").asText()).doesNotHaveDuplicates();
        }
    }
}
