package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.route.engine.Anchor;
import idusw.sbb.checkin.domain.route.engine.Candidate;
import idusw.sbb.checkin.domain.route.engine.DayPlan;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.route.engine.Haversine;
import idusw.sbb.checkin.domain.route.engine.RouteConstraints;
import idusw.sbb.checkin.domain.route.engine.SlotPlan;
import idusw.sbb.checkin.domain.route.engine.SlotType;
import idusw.sbb.checkin.domain.route.engine.TimeSlot;
import idusw.sbb.checkin.domain.route.engine.TravelCostMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteJsonWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GeoPoint ANCHOR_POINT = new GeoPoint(37.8038, 128.9087);
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 19); // 토요일

    private CandidateAdapter adapter;
    private RouteJsonWriter writer;
    private Anchor anchor;
    private List<Candidate> tours;
    private List<Candidate> foods;

    @BeforeEach
    void setUp() {
        adapter = new CandidateAdapter();
        anchor = adapter.toAnchor(node("경포비치호텔", ANCHOR_POINT, "숙소 · ₩120,000", "평점 4.2"));

        Map<String, List<ObjectNode>> nodes = new LinkedHashMap<>();
        nodes.put("food", List.of(
                node("경포동해횟집", northOf(2), "맛집 · 점심 · ₩35,000×4", "평점 4.5"),
                node("고향산천초당순두부", northOf(4), "맛집 · 점심 · ₩10,000×4", null)));
        nodes.put("tour", List.of(
                node("경포해수욕장", northOf(1), "관광지 · 1h · ₩0×4", null),
                node("경포호", northOf(3), "관광지 · 1h · ₩0×4", null)));
        List<Candidate> candidates = adapter.toCandidates(nodes);
        foods = candidates.subList(0, 2);
        tours = candidates.subList(2, 4);

        writer = RouteEngineFactory.withDefaults().jsonWriter(adapter);
    }

    private static ObjectNode node(String name, GeoPoint point, String sub, String stars) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", name);
        node.put("lat", point.latitude());
        node.put("lng", point.longitude());
        if (sub != null) {
            node.put("sub", sub);
        }
        if (stars != null) {
            node.put("stars", stars);
        }
        return node;
    }

    private static GeoPoint northOf(double km) {
        return new GeoPoint(ANCHOR_POINT.latitude() + km / 111.194, ANCHOR_POINT.longitude());
    }

    private static RouteConstraints constraints() {
        return new RouteConstraints(null, null,
                LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                null, ANCHOR_POINT, null, null, null);
    }

    /** 방문 한 곳짜리 슬롯 — 구조 검증용. 종료 시각에서 체류시간을 빼면 도착 시각이 된다. */
    private static SlotPlan singleVisit(SlotType type, Candidate visit, LocalTime endTime) {
        return new SlotPlan(type, List.of(visit), visit.location(), endTime, 0.0);
    }

    private static DayPlan dayOf(int dayIndex, SlotPlan slotPlan, LocalTime returnTime) {
        return new DayPlan(dayIndex, List.of(slotPlan), returnTime);
    }

    private List<DayPlan> threeDays() {
        return List.of(
                dayOf(0, singleVisit(SlotType.MORNING_ACTIVITY, tours.get(0), LocalTime.of(11, 0)), LocalTime.of(20, 0)),
                dayOf(1, singleVisit(SlotType.MORNING_ACTIVITY, tours.get(1), LocalTime.of(11, 0)), LocalTime.of(20, 0)),
                dayOf(2, singleVisit(SlotType.LUNCH, foods.get(0), LocalTime.of(13, 0)), LocalTime.of(16, 0)));
    }

    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> typesOf(JsonNode dayNode) {
        List<String> types = new ArrayList<>();
        for (JsonNode place : dayNode.path("places")) {
            types.add(place.has("transit") ? "transit" : place.path("type").asText());
        }
        return types;
    }

    // ── 결정 2·8 : stay 는 빼는 게 아니라 되붙이는 것 ────────────────────

    @Test
    void 첫날은_끝에만_중간날은_양끝_마지막날은_시작에만_숙소가_붙는다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        assertThat(typesOf(root.get(0))).containsExactly("tour", "transit", "stay");
        assertThat(typesOf(root.get(1))).containsExactly("stay", "transit", "tour", "transit", "stay");
        assertThat(typesOf(root.get(2))).containsExactly("stay", "transit", "food");
    }

    @Test
    void 숙소_행은_이름_좌표_숙박비를_원본에서_가져온다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        JsonNode stay = root.get(1).path("places").get(0);
        assertThat(stay.path("name").asText()).isEqualTo("경포비치호텔");
        assertThat(stay.path("sub").asText()).isEqualTo("숙소 · ₩120,000");
        assertThat(stay.path("stars").asText()).isEqualTo("평점 4.2");
        assertThat(stay.path("lat").asDouble()).isEqualTo(ANCHOR_POINT.latitude());
        assertThat(stay.path("lng").asDouble()).isEqualTo(ANCHOR_POINT.longitude());
    }

    @Test
    void 중간날_숙소_시각은_출발이_하루_시작_취침이_복귀_시각이다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        ArrayNode places = (ArrayNode) root.get(1).path("places");
        assertThat(places.get(0).path("time").asText()).isEqualTo("09:00");                 // dayStartTime
        assertThat(places.get(places.size() - 1).path("time").asText()).isEqualTo("20:00"); // DayPlan.returnTime
    }

    @Test
    void 당일치기는_숙소_행이_하나도_없다() {
        List<DayPlan> oneDay = List.of(
                dayOf(0, singleVisit(SlotType.LUNCH, foods.get(0), LocalTime.of(13, 0)), LocalTime.of(17, 0)));

        ArrayNode root = writer.write(oneDay, null, constraints(), START_DATE);

        assertThat(typesOf(root.get(0))).containsExactly("food");
    }

    // ── 결정 10-5 : transit 노드는 여기서 만들고, 내용은 뒤에서 채운다 ───

    @Test
    void 장소_사이마다_이동_플레이스홀더가_하나씩_들어간다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);
        ArrayNode places = (ArrayNode) root.get(1).path("places");

        assertThat(places.get(1).path("transit").asText()).isEqualTo("이동");
        assertThat(places.get(3).path("transit").asText()).isEqualTo("이동");
        assertThat(places.get(1).has("pathCoords")).isFalse();
    }

    @Test
    void 뒤에서_심어지는_필드는_쓰지_않는다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        for (JsonNode day : root) {
            for (JsonNode place : day.path("places")) {
                assertThat(place.has("isFound")).isFalse();
                assertThat(place.has("pathCoords")).isFalse();
            }
        }
    }

    // ── 스키마 계약 : 장소 10 + 일차 4 ──────────────────────────────────

    @Test
    void 장소_행은_계약된_필드만_채운다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);
        JsonNode place = root.get(0).path("places").get(0);

        assertThat(fieldNamesOf(place))
                .containsExactlyInAnyOrder("type", "icon", "name", "sub", "stars",
                        "key", "time", "replacePh", "lat", "lng");
        assertThat(place.path("type").asText()).isEqualTo("tour");
        assertThat(place.path("icon").asText()).isEqualTo("📍");
        assertThat(place.path("name").asText()).isEqualTo("경포해수욕장");
        assertThat(place.path("sub").asText()).isEqualTo("관광지 · 1h · ₩0×4");
        assertThat(place.path("replacePh").asText()).isEqualTo("장소 교체 요청");
        assertThat(place.path("lat").asDouble()).isEqualTo(tours.get(0).location().latitude());
    }

    @Test
    void 일차_행은_day_label_budget_places_넷이다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        assertThat(fieldNamesOf(root.get(0)))
                .containsExactlyInAnyOrder("day", "label", "budget", "places");
        assertThat(root.get(0).path("day").asInt()).isEqualTo(1);
        assertThat(root.get(2).path("day").asInt()).isEqualTo(3);
    }

    @Test
    void 평점이_없으면_평점_정보_없음으로_채운다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        assertThat(root.get(1).path("places").get(2).path("stars").asText()).isEqualTo("평점 정보 없음");
    }

    @Test
    void key는_숙소를_포함해_일차별로_1부터_이어진다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        List<String> keys = new ArrayList<>();
        for (JsonNode place : root.get(1).path("places")) {
            if (!place.has("transit")) {
                keys.add(place.path("key").asText());
            }
        }
        assertThat(keys).containsExactly("d2_1", "d2_2", "d2_3");
    }

    @Test
    void label은_날짜와_요일을_붙인다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        assertThat(root.get(0).path("label").asText()).isEqualTo("📅 Day 1 · 09/19 (토)");
        assertThat(root.get(1).path("label").asText()).isEqualTo("📅 Day 2 · 09/20 (일)");
    }

    @Test
    void 시작일을_모르면_라벨에서_날짜를_뺀다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), null);

        assertThat(root.get(0).path("label").asText()).isEqualTo("📅 Day 1");
    }

    @Test
    void budget은_그날_sub_금액의_합이다() {
        ArrayNode root = writer.write(threeDays(), anchor, constraints(), START_DATE);

        // 숙소 120,000 + 관광지 0×4 + 숙소 120,000
        assertThat(root.get(1).path("budget").asText()).isEqualTo("₩240,000");
        // 숙소 120,000 + 맛집 35,000×4
        assertThat(root.get(2).path("budget").asText()).isEqualTo("₩260,000");
    }

    // ── 시각 복원 : 엔진이 계산한 시각과 어긋나면 안 된다 ────────────────

    @Test
    void 방문_시각은_슬롯_종료_시각에서_역산한_값과_맞는다() {
        SlotPlan lunch = singleVisit(SlotType.LUNCH, foods.get(0), LocalTime.of(13, 0));

        ArrayNode root = writer.write(List.of(dayOf(0, lunch, LocalTime.of(16, 0))), null, constraints(), START_DATE);

        // 종료 13:00 − 체류 60분(FOOD 기본값) = 도착 12:00
        assertThat(root.get(0).path("places").get(0).path("time").asText()).isEqualTo("12:00");
    }

    @Test
    void DayPlanner가_돌린_결과를_그대로_되짚는다() {
        RouteConstraints constraints = constraints();
        List<TimeSlot> slots = List.of(
                new TimeSlot(SlotType.MORNING_ACTIVITY, tours),
                new TimeSlot(SlotType.LUNCH, foods));
        // 같은 팩토리에서 나온 짝이라 비용 함수가 하나다 — 이 테스트가 그 배선을 지킨다.
        RouteEngineFactory factory = RouteEngineFactory.withDefaults();
        DayPlan dayPlan = factory.dayPlanner()
                .plan(slots, ANCHOR_POINT, ANCHOR_POINT, constraints, 0, 3);

        ArrayNode root = factory.jsonWriter(adapter).write(List.of(dayPlan), null, constraints, START_DATE);

        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode place : root.get(0).path("places")) {
            if (!place.has("transit")) {
                rows.add(place);
            }
        }

        int row = 0;
        for (SlotPlan slotPlan : dayPlan.slotPlans()) {
            List<Candidate> visits = slotPlan.visitOrder();
            for (int i = 0; i < visits.size(); i++) {
                LocalTime time = LocalTime.parse(rows.get(row + i).path("time").asText());
                if (i > 0) {
                    LocalTime previous = LocalTime.parse(rows.get(row + i - 1).path("time").asText());
                    LocalTime expected = previous
                            .plusMinutes(visits.get(i - 1).dwellMinutes())
                            .plusMinutes(legMinutes(visits.get(i - 1), visits.get(i)));
                    assertThat(time).isEqualTo(expected);
                }
                assertThat(rows.get(row + i).path("name").asText()).isEqualTo(visits.get(i).name());
            }
            // 슬롯의 마지막 방문 도착 + 체류 = 엔진이 계산한 슬롯 종료 시각
            LocalTime last = LocalTime.parse(rows.get(row + visits.size() - 1).path("time").asText());
            assertThat(last.plusMinutes(visits.get(visits.size() - 1).dwellMinutes()))
                    .isEqualTo(slotPlan.endTime());
            row += visits.size();
        }
        assertThat(rows).hasSize(row);
    }

    @Test
    void 같은_입력이면_같은_JSON이_나온다() {
        String first = writer.writeJson(threeDays(), anchor, constraints(), START_DATE);
        String second = writer.writeJson(threeDays(), anchor, constraints(), START_DATE);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void 빈_슬롯만_있는_날도_숙소_행은_남는다() {
        SlotPlan empty = SlotPlan.empty(SlotType.EVENING_ACTIVITY, ANCHOR_POINT, LocalTime.of(19, 30));
        List<DayPlan> days = List.of(
                dayOf(0, empty, LocalTime.of(20, 0)),
                dayOf(1, singleVisit(SlotType.LUNCH, foods.get(0), LocalTime.of(13, 0)), LocalTime.of(20, 0)));

        ArrayNode root = writer.write(days, anchor, constraints(), START_DATE);

        assertThat(typesOf(root.get(0))).containsExactly("stay");
        assertThat(root.get(0).path("places").get(0).path("key").asText()).isEqualTo("d1_1");
    }

    private static long legMinutes(Candidate from, Candidate to) {
        return (long) Math.ceil(Haversine.distanceKm(from.location(), to.location())
                / TravelCostMetric.DEFAULT_AVERAGE_SPEED_KMH * 60.0);
    }
}
