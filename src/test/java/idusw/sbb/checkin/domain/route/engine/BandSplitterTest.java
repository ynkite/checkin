package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BandSplitterTest {

    private static final GeoPoint ANCHOR_POINT = new GeoPoint(35.0, 129.0);
    private static final Anchor ANCHOR = new Anchor("hotel-1", "해운대 숙소", ANCHOR_POINT, null, null);

    /** 위도 1도 = 자오선을 따른 대권이라 Haversine 이 정확히 111.194km 를 준다 (근사 아님). */
    private static GeoPoint northOf(GeoPoint origin, double km) {
        return new GeoPoint(origin.latitude() + km / 111.194, origin.longitude());
    }

    private Candidate candidateAt(String id, GeoPoint location) {
        return new Candidate(id, id, location, CandidateCategory.TOUR, 60, null, null, null);
    }

    private RouteConstraints constraintsWithDeparture(GeoPoint departurePoint) {
        return new RouteConstraints(
                null, null, null, null, null, null, null, ANCHOR_POINT, departurePoint, null, null);
    }

    /** minPerDay=8(결정 7-3) 을 넘겨 day0 이 스스로 채워지도록 하는 근거리 8개 — 다른 관심사 테스트를 오염시키지 않기 위한 필러. */
    private static List<Candidate> selfSufficientNear() {
        return List.of(
                new Candidate("near1", "near1", northOf(ANCHOR_POINT, 1), CandidateCategory.TOUR, 60, null, null, null),
                new Candidate("near2", "near2", northOf(ANCHOR_POINT, 1.5), CandidateCategory.TOUR, 60, null, null, null),
                new Candidate("near3", "near3", northOf(ANCHOR_POINT, 2), CandidateCategory.TOUR, 60, null, null, null),
                new Candidate("near4", "near4", northOf(ANCHOR_POINT, 2.5), CandidateCategory.TOUR, 60, null, null, null),
                new Candidate("near5", "near5", northOf(ANCHOR_POINT, 3), CandidateCategory.TOUR, 60, null, null, null),
                new Candidate("near6", "near6", northOf(ANCHOR_POINT, 3.5), CandidateCategory.TOUR, 60, null, null, null),
                new Candidate("near7", "near7", northOf(ANCHOR_POINT, 4), CandidateCategory.TOUR, 60, null, null, null),
                new Candidate("near8", "near8", northOf(ANCHOR_POINT, 4.5), CandidateCategory.TOUR, 60, null, null, null));
    }

    // ── 결정 1 : 밴드 분류 (브리핑 지정 테스트 — 더미 20개) ──────────────────

    /** 귀가거점이 앵커면 방향 정보가 없다. 결정 12 이후 마지막 날 판정에는 진짜 귀가거점이 필요하다. */
    private static final GeoPoint FAR_HOME = northOf(ANCHOR_POINT, 300);

    @Test
    void 부산_더미_20개가_거리대로_NEAR_MID_RETURN에_갈린다() {
        List<Candidate> near = selfSufficientNear();
        List<Candidate> mid = List.of(
                candidateAt("mid1", northOf(ANCHOR_POINT, 6)),
                candidateAt("mid2", northOf(ANCHOR_POINT, 8)),
                candidateAt("mid3", northOf(ANCHOR_POINT, 10)),
                candidateAt("mid4", northOf(ANCHOR_POINT, 12)),
                candidateAt("mid5", northOf(ANCHOR_POINT, 14)),
                candidateAt("mid6", northOf(ANCHOR_POINT, 16)),
                candidateAt("mid7", northOf(ANCHOR_POINT, 18)),
                candidateAt("mid8", northOf(ANCHOR_POINT, 20)),
                candidateAt("mid9", northOf(ANCHOR_POINT, 22)),
                candidateAt("mid10", northOf(ANCHOR_POINT, 24)));
        List<Candidate> ret = List.of(
                candidateAt("ret1", northOf(ANCHOR_POINT, 26)),
                candidateAt("ret2", northOf(ANCHOR_POINT, 30)),
                candidateAt("ret3", northOf(ANCHOR_POINT, 50)),
                candidateAt("ret4", northOf(ANCHOR_POINT, 80)),
                candidateAt("ret5", northOf(ANCHOR_POINT, 100)));

        List<Candidate> all = new ArrayList<>();
        all.addAll(near);
        all.addAll(mid);
        all.addAll(ret);

        BandSplitter splitter = BandSplitter.withDefaults();
        RouteConstraints constraints = constraintsWithDeparture(FAR_HOME);
        List<DailyCandidatePool> result = splitter.split(ANCHOR, constraints, all, 3);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(DailyCandidatePool::sourceBand)
                .containsExactly(DistanceBand.NEAR, DistanceBand.MID, DistanceBand.RETURN);

        // 마지막 날이 먼저 고른다 (결정 12) — 우회비용 임계 안쪽에서 귀가거점에 가까운 순으로 8곳
        DailyCandidatePool day2 = result.get(2);
        assertThat(day2.relaxed()).isFalse();
        assertThat(day2.size()).isEqualTo(BandSplitter.DEFAULT_MIN_PER_DAY);
        assertThat(day2.candidates()).allMatch(c -> constraints.detourCostKm(c.location()) <= 20.0);
        assertThat(idsOf(day2)).contains("ret1", "ret2"); // 26·30km — 가장 멀리 나아간 축
        assertThat(idsOf(day2)).doesNotContain("ret3", "ret4", "ret5"); // 50·80·100km — 36km 가드 밖

        // 남은 후보만 거리로 갈린다
        assertThat(result.get(0).candidates())
                .allMatch(c -> ANCHOR_POINT.distanceKmTo(c.location()) < BandSplitter.DEFAULT_NEAR_BOUNDARY);
        assertThat(idsOf(result.get(0))).doesNotContainAnyElementsOf(idsOf(day2));
        assertThat(idsOf(result.get(1))).doesNotContainAnyElementsOf(idsOf(day2));
    }

    // ── 결정 3 재사용 : NEAR 부족 시 MID 에서 보충 ──────────────────────────

    @Test
    void NEAR가_부족하면_MID에서_가까운_순으로_보충한다() {
        // 마지막 날이 8곳을 먼저 가져가므로(결정 12) 중거리를 넉넉히 둬야 보충 자체를 볼 수 있다.
        List<Candidate> candidates = new ArrayList<>();
        candidates.add(candidateAt("near1", northOf(ANCHOR_POINT, 2)));
        for (int k = 1; k <= 16; k++) {
            candidates.add(candidateAt("mid" + k, northOf(ANCHOR_POINT, 5.5 + k * 0.5)));
        }

        BandSplitter splitter = BandSplitter.withDefaults();
        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(FAR_HOME), candidates, 2);

        DailyCandidatePool day0 = result.get(0);
        assertThat(day0.relaxed()).isTrue();
        assertThat(day0.size()).isEqualTo(BandSplitter.DEFAULT_MIN_PER_DAY);
        assertThat(idsOf(day0)).contains("near1");
        // 보충은 앵커에서 가까운 순이다 — 마지막 날이 가져가고 남은 중거리 중 앞쪽
        assertThat(idsOf(day0)).doesNotContainAnyElementsOf(idsOf(result.get(1)));
    }

    @Test
    void 빌릴_후보조차_없으면_빈_풀을_돌려주고_죽지_않는다() {
        List<Candidate> candidates = List.of(candidateAt("near1", northOf(ANCHOR_POINT, 2)));

        BandSplitter splitter = BandSplitter.withDefaults();
        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(FAR_HOME), candidates, 2);

        // 결정 12 이후로는 마지막 날이 먼저 고르므로, 하나뿐인 후보는 마지막 날이 가져가고 첫날이 빈다.
        assertThat(result).hasSize(2);
        assertThat(result.get(0).candidates()).isEmpty();
        assertThat(idsOf(result.get(1))).containsExactly("near1");
    }

    // ── 결정 5-(2) : 방위각 간격 컷으로 중간 날 분리 ─────────────────────────

    @Test
    void 중간_날은_방위각_간격이_가장_큰_지점에서_갈린다() {
        // 그룹A: 앵커 기준 북동쪽으로 촘촘히 모여 있음 (8개 — minPerDay 를 밴드 백필 없이 채움)
        List<Candidate> groupA = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double offset = 0.09 + k * 0.005;
            groupA.add(candidateAt("a" + k, new GeoPoint(35.0 + offset, 129.0 + offset)));
        }
        // 그룹B: 앵커 기준 남서쪽으로 촘촘히 모여 있음 — A 와는 방위각이 크게 벌어진다
        List<Candidate> groupB = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double offset = 0.09 + k * 0.005;
            groupB.add(candidateAt("b" + k, new GeoPoint(35.0 - offset, 129.0 - offset)));
        }

        // 귀가 방향(북쪽)으로 8곳 — 마지막 날이 이걸 가져가서 A·B 덩이를 건드리지 않게 한다
        List<Candidate> ret = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            ret.add(candidateAt("ret" + k, northOf(ANCHOR_POINT, 35 - k * 1.5)));
        }

        List<Candidate> all = new ArrayList<>();
        all.addAll(selfSufficientNear());
        all.addAll(groupA);
        all.addAll(groupB);
        all.addAll(ret);

        BandSplitter splitter = BandSplitter.withDefaults();
        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(FAR_HOME), all, 4);

        assertThat(result).hasSize(4);
        DailyCandidatePool middle1 = result.get(1);
        DailyCandidatePool middle2 = result.get(2);

        Set<String> groupAIds = groupA.stream().map(Candidate::id).collect(Collectors.toSet());
        Set<String> groupBIds = groupB.stream().map(Candidate::id).collect(Collectors.toSet());

        assertThat(Set.of(idsOf(middle1), idsOf(middle2)))
                .isEqualTo(Set.of(groupAIds, groupBIds));
    }

    // ── 결정 2/4 : 마지막 날 우회비용 필터 + 완화 폴백 (명시적 임계값) ───────

    @Test
    void 임계_이내_후보가_충분하면_그대로_쓰고_완화하지_않는다() {
        GeoPoint departurePoint = northOf(ANCHOR_POINT, 60);
        // 귀가 경로(0~60km 자오선) 위에 그대로 있는 후보 8개 — 우회비용이 0에 가깝다.
        // 전부 절대거리 가드(36km) 안쪽이라 가드에 걸리지 않는다 (결정 11-(2)).
        List<Candidate> onPath = new ArrayList<>();
        double[] onPathKm = {26, 27, 28, 29, 30, 31, 32, 33};
        for (int i = 0; i < onPathKm.length; i++) {
            onPath.add(candidateAt("onpath" + i, northOf(ANCHOR_POINT, onPathKm[i])));
        }

        List<Candidate> all = new ArrayList<>(selfSufficientNear());
        all.addAll(onPath);

        BandSplitter splitter = new BandSplitter(
                BandSplitter.DEFAULT_NEAR_BOUNDARY, BandSplitter.DEFAULT_MID_BOUNDARY,
                BandSplitter.DEFAULT_MIN_PER_DAY, 5.0, Haversine::distanceKm);

        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(departurePoint), all, 2);

        DailyCandidatePool lastDay = result.get(1);
        assertThat(lastDay.relaxed()).isFalse();
        assertThat(idsOf(lastDay)).containsExactlyInAnyOrderElementsOf(
                onPath.stream().map(Candidate::id).toList());
    }

    @Test
    void 임계_이내_후보가_모자라면_우회비용_오름차순으로_완화해서_채운다() {
        GeoPoint departurePoint = northOf(ANCHOR_POINT, 10);
        // 귀가 방향과 무관한, 동쪽으로 점점 더 멀어지는 후보 10개 — 전부 우회비용이 크고, 뒤로 갈수록 더 크다.
        // 25~34km 라 RETURN 밴드이면서 절대거리 가드(36km) 안쪽이다.
        List<Candidate> offPath = new ArrayList<>();
        double[] lngOffsets = {0.28, 0.29, 0.30, 0.31, 0.32, 0.33, 0.34, 0.35, 0.36, 0.37};
        for (int i = 0; i < lngOffsets.length; i++) {
            offPath.add(candidateAt("off" + i,
                    new GeoPoint(ANCHOR_POINT.latitude(), ANCHOR_POINT.longitude() + lngOffsets[i])));
        }

        // 근거리 필러를 넣지 않는다 — 결정 12 이후 숙소 옆 후보는 우회비용이 0에 가까워
        // 임계를 통과해버리고, 그러면 완화 폴백 자체가 안 돈다.
        List<Candidate> all = new ArrayList<>(offPath);

        BandSplitter splitter = new BandSplitter(
                BandSplitter.DEFAULT_NEAR_BOUNDARY, BandSplitter.DEFAULT_MID_BOUNDARY,
                BandSplitter.DEFAULT_MIN_PER_DAY, 5.0, Haversine::distanceKm);

        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(departurePoint), all, 2);

        DailyCandidatePool lastDay = result.get(1);
        assertThat(lastDay.relaxed()).isTrue();
        assertThat(lastDay.size()).isEqualTo(BandSplitter.DEFAULT_MIN_PER_DAY);
        // 우회비용은 동쪽으로 갈수록 커지므로, 가장 멀리 있는 off8·off9 는 8개 완화 채움에서 빠진다
        assertThat(idsOf(lastDay)).doesNotContain("off8", "off9");
    }

    // ── 결정 11-(2) : 귀가 밴드 절대거리 가드 (뒤 단계 FAR_LIMIT 40km 와의 접점) ──

    @Test
    void 앵커에서_36km를_넘는_귀가_후보는_애초에_풀에_안_들어온다() {
        GeoPoint departurePoint = northOf(ANCHOR_POINT, 120);
        List<Candidate> all = new ArrayList<>(selfSufficientNear());
        all.add(candidateAt("in35", northOf(ANCHOR_POINT, 35)));   // 가드 안쪽
        all.add(candidateAt("out37", northOf(ANCHOR_POINT, 37)));  // 가드 바깥
        all.add(candidateAt("out60", northOf(ANCHOR_POINT, 60)));  // 우회비용은 0에 가깝지만 너무 멀다

        List<DailyCandidatePool> result = BandSplitter.withDefaults().split(
                ANCHOR, constraintsWithDeparture(departurePoint), all, 2);

        // in35 는 귀가거점에 가장 가까워 마지막 날 1순위, out37·out60 은 가드에서 이미 빠졌다
        assertThat(idsOf(result.get(1))).contains("in35").doesNotContain("out37", "out60");
        assertThat(result).flatExtracting(DailyCandidatePool::candidates)
                .extracting(Candidate::id).doesNotContain("out37", "out60");
    }

    @Test
    void 가드에_걸린_후보는_완화_폴백으로도_안_들어온다() {
        GeoPoint departurePoint = northOf(ANCHOR_POINT, 120);
        List<Candidate> all = new ArrayList<>(selfSufficientNear());
        for (int k = 0; k < 5; k++) {
            all.add(candidateAt("far" + k, northOf(ANCHOR_POINT, 40 + k * 10)));
        }

        List<DailyCandidatePool> result = BandSplitter.withDefaults().split(
                ANCHOR, constraintsWithDeparture(departurePoint), all, 2);

        // 완화는 "임계 이하가 모자라면 우회비용 순으로 채운다" 지 "가드를 푼다" 가 아니다.
        assertThat(result).flatExtracting(DailyCandidatePool::candidates)
                .extracting(Candidate::id)
                .doesNotContain("far0", "far1", "far2", "far3", "far4");
    }

    @Test
    void 가드에_걸린_후보는_중간_날_보충에도_안_쓰인다() {
        List<Candidate> all = new ArrayList<>(selfSufficientNear());
        all.add(candidateAt("mid1", northOf(ANCHOR_POINT, 10)));
        all.add(candidateAt("mid2", northOf(ANCHOR_POINT, 12)));
        all.add(candidateAt("in30", northOf(ANCHOR_POINT, 30)));
        all.add(candidateAt("out45", northOf(ANCHOR_POINT, 45)));
        all.add(candidateAt("out60", northOf(ANCHOR_POINT, 60)));

        // 중간 날 둘이 minPerDay 를 못 채워 보충(topUp)이 반드시 돈다 — 그 donor 목록에 RETURN 밴드가 있다.
        List<DailyCandidatePool> result = BandSplitter.withDefaults().split(
                ANCHOR, constraintsWithDeparture(northOf(ANCHOR_POINT, 120)), all, 4);

        List<String> everyId = result.stream().flatMap(pool -> idsOf(pool).stream()).toList();
        assertThat(everyId).contains("in30").doesNotContain("out45", "out60");
    }

    // ── 결정 5-(3) : 비율 기반 기본 임계값 — 여행이 길수록 임계도 늘어난다 ────

    @Test
    void 기본_우회비용_임계는_귀가거리에_비례해서_늘어난다() {
        // 같은 후보(귀가 경로에서 살짝 벗어난 지점) 8개 인데 귀가거리가 짧으면 걸러지고 길면 통과한다.
        List<Candidate> returnCandidates = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double latOffset = 0.2428 + k * 0.0045;
            returnCandidates.add(candidateAt("r" + k, new GeoPoint(35.0 + latOffset, 129.1763)));
        }

        // 여기도 근거리 필러를 빼야 임계의 효과만 보인다 (위와 같은 이유)
        List<Candidate> all = new ArrayList<>(returnCandidates);

        BandSplitter splitter = BandSplitter.withDefaults();

        GeoPoint shortDeparture = northOf(ANCHOR_POINT, 10);
        GeoPoint longDeparture = northOf(ANCHOR_POINT, 60);

        DailyCandidatePool shortTrip = splitter.split(
                ANCHOR, constraintsWithDeparture(shortDeparture), all, 2).get(1);
        DailyCandidatePool longTrip = splitter.split(
                ANCHOR, constraintsWithDeparture(longDeparture), all, 2).get(1);

        assertThat(shortTrip.relaxed()).isTrue();
        assertThat(longTrip.relaxed()).isFalse();
        assertThat(idsOf(longTrip)).containsExactlyInAnyOrderElementsOf(
                returnCandidates.stream().map(Candidate::id).toList());
    }

    // ── 결정 1 : 일수(N)에 따른 접기 구조 ────────────────────────────────

    @Test
    void 당일치기는_근거리_하루뿐이다() {
        BandSplitter splitter = BandSplitter.withDefaults();
        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(ANCHOR_POINT), selfSufficientNear(), 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceBand()).isEqualTo(DistanceBand.NEAR);
    }

    @Test
    void 일박이일은_중거리_없이_근거리와_귀가만_있다() {
        List<Candidate> candidates = new ArrayList<>(selfSufficientNear());
        candidates.add(candidateAt("ret1", northOf(ANCHOR_POINT, 30)));
        candidates.add(candidateAt("ret2", northOf(ANCHOR_POINT, 40)));
        candidates.add(candidateAt("ret3", northOf(ANCHOR_POINT, 50)));

        BandSplitter splitter = BandSplitter.withDefaults();
        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(ANCHOR_POINT), candidates, 2);

        assertThat(result).extracting(DailyCandidatePool::sourceBand)
                .containsExactly(DistanceBand.NEAR, DistanceBand.RETURN);
    }

    @Test
    void 삼박사일은_중거리가_두_덩이다() {
        List<Candidate> candidates = new ArrayList<>(selfSufficientNear());
        candidates.add(candidateAt("mid1", new GeoPoint(35.09, 129.09)));
        candidates.add(candidateAt("mid2", new GeoPoint(35.10, 129.10)));
        candidates.add(candidateAt("mid3", new GeoPoint(35.11, 129.11)));
        candidates.add(candidateAt("mid4", new GeoPoint(34.91, 128.91)));
        candidates.add(candidateAt("mid5", new GeoPoint(34.90, 128.90)));
        candidates.add(candidateAt("mid6", new GeoPoint(34.89, 128.89)));
        candidates.add(candidateAt("ret1", northOf(ANCHOR_POINT, 30)));
        candidates.add(candidateAt("ret2", northOf(ANCHOR_POINT, 40)));
        candidates.add(candidateAt("ret3", northOf(ANCHOR_POINT, 50)));

        BandSplitter splitter = BandSplitter.withDefaults();
        List<DailyCandidatePool> result = splitter.split(
                ANCHOR, constraintsWithDeparture(ANCHOR_POINT), candidates, 4);

        assertThat(result).extracting(DailyCandidatePool::sourceBand)
                .containsExactly(DistanceBand.NEAR, DistanceBand.MID, DistanceBand.MID, DistanceBand.RETURN);
        assertThat(result).extracting(DailyCandidatePool::dayIndex)
                .containsExactly(0, 1, 2, 3);
    }

    // ── 당일치기에 숙소가 없는 경우 (결정 5 추가사항) ─────────────────────

    @Test
    void 앵커가_없으면_도착지점을_앵커_대신_쓴다() {
        List<Candidate> candidates = selfSufficientNear();

        BandSplitter splitter = BandSplitter.withDefaults();

        List<DailyCandidatePool> result = splitter.split(
                null, constraintsWithDeparture(ANCHOR_POINT), candidates, 1);

        assertThat(result).hasSize(1);
        assertThat(idsOf(result.get(0))).containsExactlyInAnyOrderElementsOf(
                candidates.stream().map(Candidate::id).toList());
    }

    // ── 생성자 검증 ─────────────────────────────────────────────────────

    @Test
    void nearBoundary가_0이하면_예외() {
        assertThatThrownBy(() -> new BandSplitter(0, 25, 3, null, Haversine::distanceKm))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void midBoundary가_nearBoundary보다_작거나_같으면_예외() {
        assertThatThrownBy(() -> new BandSplitter(10, 10, 3, null, Haversine::distanceKm))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minPerDay가_0이하면_예외() {
        assertThatThrownBy(() -> new BandSplitter(5, 25, 0, null, Haversine::distanceKm))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void costMetric이_없으면_예외() {
        assertThatThrownBy(() -> new BandSplitter(5, 25, 3, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalDays가_1보다_작으면_예외() {
        BandSplitter splitter = BandSplitter.withDefaults();
        assertThatThrownBy(() -> splitter.split(ANCHOR, constraintsWithDeparture(ANCHOR_POINT), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Set<String> idsOf(DailyCandidatePool pool) {
        return pool.candidates().stream().map(Candidate::id).collect(Collectors.toSet());
    }
}
