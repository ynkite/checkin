package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.route.engine.Anchor;
import idusw.sbb.checkin.domain.route.engine.Candidate;
import idusw.sbb.checkin.domain.route.engine.CandidateCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode node(String name, double lat, double lng, String sub, String stars) {
        ObjectNode node = MAPPER.createObjectNode();
        if (name != null) {
            node.put("name", name);
        }
        node.put("lat", lat);
        node.put("lng", lng);
        if (sub != null) {
            node.put("sub", sub);
        }
        if (stars != null) {
            node.put("stars", stars);
        }
        return node;
    }

    private static Map<String, List<ObjectNode>> byType(String type, ObjectNode... nodes) {
        Map<String, List<ObjectNode>> map = new LinkedHashMap<>();
        map.put(type, List.of(nodes));
        return map;
    }

    // ── 숙소는 후보가 아니라 앵커다 (결정 2) ─────────────────────────────

    @Test
    void food_cafe_tour만_후보가_되고_stay는_빠진다() {
        Map<String, List<ObjectNode>> nodes = new LinkedHashMap<>();
        nodes.put("stay", List.of(node("경포비치호텔", 37.80, 128.90, "숙소 · ₩120,000", null)));
        nodes.put("food", List.of(node("경포동해횟집", 37.80, 128.91, "맛집 · 점심 · ₩35,000×4", null)));
        nodes.put("cafe", List.of(node("카페툇마루", 37.79, 128.90, "카페 · ₩6,500×4", null)));
        nodes.put("tour", List.of(node("경포해수욕장", 37.81, 128.91, "관광지 · 1h · ₩0×4", null)));

        List<Candidate> candidates = new CandidateAdapter().toCandidates(nodes);

        assertThat(candidates).extracting(Candidate::name)
                .containsExactly("경포동해횟집", "카페툇마루", "경포해수욕장");
        assertThat(candidates).extracting(Candidate::category)
                .containsExactly(CandidateCategory.FOOD, CandidateCategory.CAFE, CandidateCategory.TOUR);
    }

    @Test
    void 숙소_노드는_앵커로_바뀌고_역참조에도_남는다() {
        CandidateAdapter adapter = new CandidateAdapter();
        ObjectNode stay = node("경포비치호텔", 37.8038, 128.9087, "숙소 · ₩120,000", "평점 4.2");

        Anchor anchor = adapter.toAnchor(stay);

        assertThat(anchor).isNotNull();
        assertThat(anchor.name()).isEqualTo("경포비치호텔");
        assertThat(anchor.location().latitude()).isEqualTo(37.8038);
        assertThat(adapter.originOf(anchor.id()).path("sub").asText()).isEqualTo("숙소 · ₩120,000");
    }

    @Test
    void 이름이나_좌표가_없는_숙소는_앵커가_되지_않는다() {
        CandidateAdapter adapter = new CandidateAdapter();

        assertThat(adapter.toAnchor(null)).isNull();
        assertThat(adapter.toAnchor(node(null, 37.8, 128.9, null, null))).isNull();
        assertThat(adapter.toAnchor(MAPPER.createObjectNode().put("name", "좌표없는호텔"))).isNull();
    }

    // ── 결정 10-4 : 엔진에 필드를 늘리지 않고 원본을 되찾는다 ───────────

    @Test
    void 역참조로_원본의_sub와_stars를_되찾는다() {
        CandidateAdapter adapter = new CandidateAdapter();
        ObjectNode origin = node("경포동해횟집", 37.80, 128.91, "맛집 · 점심 · ₩35,000×4", "평점 4.5");

        List<Candidate> candidates = adapter.toCandidates(byType("food", origin));

        ObjectNode found = adapter.originOf(candidates.get(0).id());
        assertThat(found.path("sub").asText()).isEqualTo("맛집 · 점심 · ₩35,000×4");
        assertThat(found.path("stars").asText()).isEqualTo("평점 4.5");
    }

    @Test
    void 모르는_id는_역참조가_비어_있다() {
        assertThat(new CandidateAdapter().originOf("food-999")).isNull();
    }

    // ── 경계 방어 : 카카오 응답이 깨져 있어도 엔진으로 안 흘려보낸다 ────

    @Test
    void 이름이나_좌표가_없는_후보는_건너뛰고_번호는_안_밀린다() {
        ObjectNode valid = node("경포해수욕장", 37.80, 128.91, null, null);
        ObjectNode noName = node(null, 37.81, 128.92, null, null);
        ObjectNode noCoordinate = MAPPER.createObjectNode();
        noCoordinate.put("name", "좌표없는곳");
        ObjectNode another = node("경포호", 37.79, 128.90, null, null);

        CandidateAdapter adapter = new CandidateAdapter();
        List<Candidate> candidates = adapter.toCandidates(byType("tour", valid, noName, noCoordinate, another));

        assertThat(candidates).extracting(Candidate::name).containsExactly("경포해수욕장", "경포호");
        assertThat(candidates).extracting(Candidate::id).containsExactly("tour-000", "tour-001");
    }

    @Test
    void 위경도_범위를_벗어난_후보도_건너뛴다() {
        List<Candidate> candidates = new CandidateAdapter()
                .toCandidates(byType("tour", node("이상치", 999.0, 128.9, null, null)));

        assertThat(candidates).isEmpty();
    }

    @Test
    void 후보가_없으면_빈_목록이다() {
        assertThat(new CandidateAdapter().toCandidates(null)).isEmpty();
        assertThat(new CandidateAdapter().toCandidates(Map.of())).isEmpty();
    }

    // ── 결정 8 : 동점 처리가 id 사전순이라 id 순서가 곧 결정론이다 ──────

    @Test
    void id는_10개를_넘겨도_사전순이_입력순과_같다() {
        List<ObjectNode> nodes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            nodes.add(node("관광지" + i, 37.80 + i * 0.001, 128.91, null, null));
        }
        Map<String, List<ObjectNode>> byType = new LinkedHashMap<>();
        byType.put("tour", nodes);

        List<String> ids = new CandidateAdapter().toCandidates(byType).stream().map(Candidate::id).toList();

        assertThat(ids).containsExactly(
                "tour-000", "tour-001", "tour-002", "tour-003", "tour-004", "tour-005",
                "tour-006", "tour-007", "tour-008", "tour-009", "tour-010", "tour-011");
        assertThat(ids).isSorted();
    }

    // ── 결정 9-2 : 체류시간은 비워두고 읽는 시점에 기본값이 나와야 한다 ─

    @Test
    void 체류시간은_비워두고_카테고리_기본값이_읽힌다() {
        Map<String, List<ObjectNode>> nodes = new LinkedHashMap<>();
        nodes.put("food", List.of(node("횟집", 37.80, 128.91, null, null)));
        nodes.put("cafe", List.of(node("카페", 37.79, 128.90, null, null)));
        nodes.put("tour", List.of(node("해변", 37.81, 128.92, null, null)));

        List<Candidate> candidates = new CandidateAdapter().toCandidates(nodes);

        assertThat(candidates).extracting(Candidate::dwellMinutes).containsExactly(60, 40, 90);
    }

    /**
     * 결정 15 : 카카오가 영업시간을 안 주므로 어댑터가 카테고리 기본값을 채운다. 비워 두면
     * 엔진의 "openTime == null → 상시 영업" 계약 때문에 19:30 에 실내 관광지가 배치된다.
     */
    @Test
    void 카테고리_기본_영업시간을_채운다() {
        Map<String, List<ObjectNode>> nodes = new LinkedHashMap<>();
        nodes.put("food", List.of(node("횟집", 37.80, 128.91, null, null)));
        nodes.put("cafe", List.of(node("카페", 37.79, 128.90, null, null)));
        nodes.put("tour", List.of(node("해변", 37.81, 128.92, null, null)));

        List<Candidate> candidates = new CandidateAdapter().toCandidates(nodes);

        assertThat(candidates).extracting(Candidate::openTime)
                .containsExactly(LocalTime.of(11, 0), LocalTime.of(10, 0), LocalTime.of(9, 0));
        assertThat(candidates).extracting(Candidate::closeTime)
                .containsExactly(LocalTime.of(21, 0), LocalTime.of(21, 0), LocalTime.of(18, 0));
    }

    @Test
    void 관광지는_저녁_활동_창과_겹치지_않는다() {
        Candidate tour = new CandidateAdapter()
                .toCandidates(byType("tour", node("오죽헌", 37.80, 128.91, null, null))).get(0);

        assertThat(tour.isOpenAt(java.time.DayOfWeek.SATURDAY, LocalTime.of(19, 30))).isFalse();
        assertThat(tour.isOpenAt(java.time.DayOfWeek.SATURDAY, LocalTime.of(13, 30))).isTrue();
    }

    @Test
    void 휴무일은_여전히_비어_있다() {
        // 카카오도 관광공사도 휴무일을 안 주고, 엔진도 달력 날짜를 몰라 판정하지 않는다
        List<Candidate> candidates = new CandidateAdapter()
                .toCandidates(byType("tour", node("해변", 37.80, 128.91, null, null)));

        assertThat(candidates.get(0).closedDays()).isEmpty();
    }

    @Test
    void jsonType은_엔진_카테고리를_프론트_type으로_되돌린다() {
        assertThat(CandidateAdapter.jsonType(CandidateCategory.FOOD)).isEqualTo("food");
        assertThat(CandidateAdapter.jsonType(CandidateCategory.CAFE)).isEqualTo("cafe");
        assertThat(CandidateAdapter.jsonType(CandidateCategory.TOUR)).isEqualTo("tour");
    }
}
