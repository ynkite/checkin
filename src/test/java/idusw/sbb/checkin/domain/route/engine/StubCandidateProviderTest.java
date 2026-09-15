package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubCandidateProviderTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 3);
    /** 09_시도_대표좌표.md 의 11번 — before 3도시가 전부 출발지 서울이다. */
    private static final GeoPoint SEOUL = new GeoPoint(37.5665, 126.9780);

    private final StubCandidateProvider provider = new StubCandidateProvider();

    // ── 지역당 25개 이상 ─────────────────────────────────────────────────

    @Test
    void 부산_경주_강릉_각각_25개_이상이다() {
        assertThat(provider.findCandidates("부산", START, END).size()).isGreaterThanOrEqualTo(25);
        assertThat(provider.findCandidates("경주", START, END).size()).isGreaterThanOrEqualTo(25);
        assertThat(provider.findCandidates("강릉", START, END).size()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void 지원하지_않는_지역이면_예외() {
        assertThatThrownBy(() -> provider.findCandidates("서울", START, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void region이_없으면_예외() {
        assertThatThrownBy(() -> provider.findCandidates(null, START, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 지역 안에서 id 중복이 없다 ───────────────────────────────────────

    @Test
    void 지역_안에서_id가_전부_고유하다() {
        for (String region : List.of("부산", "경주", "강릉")) {
            List<Candidate> candidates = provider.findCandidates(region, START, END);
            Set<String> ids = candidates.stream().map(Candidate::id).collect(Collectors.toSet());
            assertThat(ids).as(region).hasSameSizeAs(candidates);
        }
    }

    // ── 카테고리·영업시간이 섞여 있다 ───────────────────────────────────────

    @Test
    void 각_지역에_FOOD_TOUR_CAFE가_전부_있다() {
        for (String region : List.of("부산", "경주", "강릉")) {
            Set<CandidateCategory> categories = provider.findCandidates(region, START, END).stream()
                    .map(Candidate::category)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(CandidateCategory.class)));
            assertThat(categories).as(region)
                    .containsExactlyInAnyOrder(CandidateCategory.FOOD, CandidateCategory.TOUR, CandidateCategory.CAFE);
        }
    }

    @Test
    void 각_지역에_상시개방과_영업시간_지정이_둘다_있다() {
        for (String region : List.of("부산", "경주", "강릉")) {
            List<Candidate> candidates = provider.findCandidates(region, START, END);
            assertThat(candidates).as(region).anyMatch(c -> c.openTime() == null);
            assertThat(candidates).as(region).anyMatch(c -> c.openTime() != null);
        }
    }

    // ── BandSplitter가 실제로 갈라야 한다 (이 작업의 핵심 요구사항) ───────────

    @Test
    void 부산_후보는_NEAR_MID_RETURN_전부로_실제로_갈린다() {
        assertBandSplitterActuallySplits("부산", new GeoPoint(35.1587, 129.1604));
    }

    @Test
    void 경주_후보는_NEAR_MID_RETURN_전부로_실제로_갈린다() {
        assertBandSplitterActuallySplits("경주", new GeoPoint(35.8380, 129.2748));
    }

    @Test
    void 강릉_후보는_NEAR_MID_RETURN_전부로_실제로_갈린다() {
        assertBandSplitterActuallySplits("강릉", new GeoPoint(37.8058, 128.8968));
    }

    /**
     * 부산 귀가 후보 셋은 거리로는 27·29·34km 로 고만고만한데, 서울로 돌아가는 방향이냐로 갈린다.
     * 거리 대신 우회비용을 쓴 이유가 이 지점이다 (결정 2).
     */
    @Test
    void 부산_귀가_후보는_거리가_아니라_서울_방향_우회비용으로_갈린다() {
        GeoPoint anchorPoint = new GeoPoint(35.1587, 129.1604);
        List<Candidate> candidates = provider.findCandidates("부산", START, END);
        Anchor anchor = new Anchor("부산-anchor", "부산 숙소", anchorPoint, null, null);
        RouteConstraints constraints = new RouteConstraints(
                null, null, null, null, null, null, null, anchorPoint, SEOUL, null, null);

        // 0.3 × 331km ≈ 99 라 계수가 아니라 clamp 상한이 값을 정한다 (09_시도_대표좌표.md)
        assertThat(BandSplitter.withDefaults().resolveMaxDetourCost(anchorPoint, constraints)).isEqualTo(20.0);

        GeoPoint suroTomb = locationOf(candidates, "김해수로왕릉");
        GeoPoint ganjeolgot = locationOf(candidates, "간절곶");
        GeoPoint gadeokdo = locationOf(candidates, "가덕도대항전망대");

        // 거리는 셋 다 가드(36km) 안쪽인데 우회비용은 다섯 배까지 벌어진다
        assertThat(anchorPoint.distanceKmTo(gadeokdo)).isLessThan(BandSplitter.MAX_RETURN_DISTANCE_KM);
        assertThat(constraints.detourCostKm(suroTomb)).isLessThan(20.0);      // 약 5.6 — 서울 쪽
        assertThat(constraints.detourCostKm(ganjeolgot)).isBetween(20.0, 25.0); // 약 22.2 — 북동
        assertThat(constraints.detourCostKm(gadeokdo)).isGreaterThan(30.0);   // 약 30.8 — 남서, 반대편

        // 스텁에 임계(20) 이내 후보가 수로왕릉 하나뿐이라 기본 minPerDay(8) 로는 완화 폴백이 셋을 다
        // 끌어온다. 우회비용 순위가 실제로 작동하는지 보려면 최소치를 2로 낮춰야 한다.
        BandSplitter splitter = new BandSplitter(BandSplitter.DEFAULT_NEAR_BOUNDARY,
                BandSplitter.DEFAULT_MID_BOUNDARY, 2, null, Haversine::distanceKm);
        DailyCandidatePool lastDay = splitter.split(anchor, constraints, candidates, 3).get(2);

        assertThat(idsOf(lastDay))
                .contains("busan-gimhae-suro-tomb", "busan-ganjeolgot")
                .doesNotContain("busan-gadeokdo-daehang");
    }

    private static Set<String> idsOf(DailyCandidatePool pool) {
        return pool.candidates().stream().map(Candidate::id).collect(Collectors.toSet());
    }

    private static GeoPoint locationOf(List<Candidate> candidates, String name) {
        return candidates.stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("스텁에 없는 후보: " + name))
                .location();
    }

    private void assertBandSplitterActuallySplits(String region, GeoPoint anchorPoint) {
        List<Candidate> candidates = provider.findCandidates(region, START, END);
        Anchor anchor = new Anchor(region + "-anchor", region + " 숙소", anchorPoint, null, null);
        // 귀가거점을 서울로 둔다 — 없으면 d(숙소,귀가거점)=0 이라 방향 정보가 아예 없다 (결정 10-8)
        RouteConstraints constraints = new RouteConstraints(
                null, null, null, null, null, null, null, anchorPoint, SEOUL, null, null);

        List<DailyCandidatePool> result = BandSplitter.withDefaults().split(anchor, constraints, candidates, 3);

        assertThat(result).as(region).hasSize(3);
        assertThat(result.get(0).sourceBand()).as(region).isEqualTo(DistanceBand.NEAR);
        assertThat(result.get(0).size()).as(region + " NEAR").isGreaterThan(0);
        assertThat(result.get(1).sourceBand()).as(region).isEqualTo(DistanceBand.MID);
        assertThat(result.get(1).size()).as(region + " MID").isGreaterThan(0);
        assertThat(result.get(2).sourceBand()).as(region).isEqualTo(DistanceBand.RETURN);
        assertThat(result.get(2).size()).as(region + " RETURN").isGreaterThan(0);
        // 결정 11-(2) : 가드보다 먼 후보는 귀가 풀에 남아 있으면 안 된다 (뒤 단계가 지울 장소다)
        assertThat(result.get(2).candidates()).as(region + " 가드")
                .allMatch(c -> anchorPoint.distanceKmTo(c.location()) <= BandSplitter.MAX_RETURN_DISTANCE_KM);
    }
}
