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

    /**
     * 부산 스텁의 귀가 후보는 통도사(38km)·진해(42km)·창원(44km)·밀양(50km)·거제(68km)·통영(75km) 여섯 곳이라
     * <b>전부 절대거리 가드(36km, 결정 11-(2)) 밖</b>이다. 가드는 뒤 단계 {@code postProcessRoute} 가
     * 40km 도로거리로 지울 장소를 애초에 안 넣는 규칙이라, 여기서 귀가 밴드가 비는 게 정상 동작이다.
     * 실제 후보는 카카오 반경 검색(≤20km)이 주므로 이 상황은 스텁 데이터 특성에 가깝다.
     */
    @Test
    void 부산_후보는_근거리_중거리로_갈리고_귀가_밴드는_가드로_비워진다() {
        GeoPoint anchorPoint = new GeoPoint(35.1587, 129.1604);
        List<Candidate> candidates = provider.findCandidates("부산", START, END);
        Anchor anchor = new Anchor("부산-anchor", "부산 숙소", anchorPoint, null, null);
        RouteConstraints constraints = new RouteConstraints(
                null, null, null, null, null, null, null, anchorPoint, null, null, null);

        List<DailyCandidatePool> result = BandSplitter.withDefaults().split(anchor, constraints, candidates, 3);

        assertThat(result.get(0).size()).isGreaterThan(0);
        assertThat(result.get(1).size()).isGreaterThan(0);
        assertThat(result.get(2).sourceBand()).isEqualTo(DistanceBand.RETURN);
        assertThat(result.get(2).candidates()).isEmpty();
        assertThat(candidates).anyMatch(c -> anchorPoint.distanceKmTo(c.location()) > BandSplitter.MAX_RETURN_DISTANCE_KM);
    }

    @Test
    void 경주_후보는_NEAR_MID_RETURN_전부로_실제로_갈린다() {
        assertBandSplitterActuallySplits("경주", new GeoPoint(35.8380, 129.2748));
    }

    @Test
    void 강릉_후보는_NEAR_MID_RETURN_전부로_실제로_갈린다() {
        assertBandSplitterActuallySplits("강릉", new GeoPoint(37.8058, 128.8968));
    }

    private void assertBandSplitterActuallySplits(String region, GeoPoint anchorPoint) {
        List<Candidate> candidates = provider.findCandidates(region, START, END);
        Anchor anchor = new Anchor(region + "-anchor", region + " 숙소", anchorPoint, null, null);
        RouteConstraints constraints = new RouteConstraints(
                null, null, null, null, null, null, null, anchorPoint, null, null, null);

        List<DailyCandidatePool> result = BandSplitter.withDefaults().split(anchor, constraints, candidates, 3);

        assertThat(result).as(region).hasSize(3);
        assertThat(result.get(0).sourceBand()).as(region).isEqualTo(DistanceBand.NEAR);
        assertThat(result.get(0).size()).as(region + " NEAR").isGreaterThan(0);
        assertThat(result.get(1).sourceBand()).as(region).isEqualTo(DistanceBand.MID);
        assertThat(result.get(1).size()).as(region + " MID").isGreaterThan(0);
        assertThat(result.get(2).sourceBand()).as(region).isEqualTo(DistanceBand.RETURN);
        assertThat(result.get(2).size()).as(region + " RETURN").isGreaterThan(0);
    }
}
