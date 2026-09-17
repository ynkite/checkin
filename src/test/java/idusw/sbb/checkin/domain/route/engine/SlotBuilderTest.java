package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotBuilderTest {

    private static final GeoPoint ANCHOR_POINT = new GeoPoint(35.0, 129.0);
    private static final Anchor ANCHOR = new Anchor("hotel-1", "해운대 숙소", ANCHOR_POINT, null, null);

    private static GeoPoint northOf(GeoPoint origin, double km) {
        return new GeoPoint(origin.latitude() + km / 111.194, origin.longitude());
    }

    /** 근사 동서 오프셋 — km 값이 클수록(음수면 서쪽) 오차보다 훨씬 큰 여유를 두고 쓴다. */
    private static GeoPoint eastOf(GeoPoint origin, double km) {
        double lngPerKm = 1.0 / (111.194 * Math.cos(Math.toRadians(origin.latitude())));
        return new GeoPoint(origin.latitude(), origin.longitude() + km * lngPerKm);
    }

    private static Candidate candidate(String id, GeoPoint location, CandidateCategory category,
                                        LocalTime openTime, LocalTime closeTime) {
        return new Candidate(id, id, location, category, 60, openTime, closeTime, null);
    }

    private static Candidate alwaysOpen(String id, GeoPoint location, CandidateCategory category) {
        return candidate(id, location, category, null, null);
    }

    /** firstDayArrival/lastDayDepartureTime 만 다르게 받는 표준 제약 (점심 11:30~13:30, 저녁 18:00~19:30). */
    private static RouteConstraints constraints(LocalDateTime firstDayArrival, LocalTime lastDayDepartureTime) {
        return new RouteConstraints(
                firstDayArrival, lastDayDepartureTime,
                LocalTime.of(11, 30), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                null, ANCHOR_POINT, null, null, null);
    }

    private static DailyCandidatePool poolOf(int dayIndex, List<Candidate> candidates) {
        return new DailyCandidatePool(dayIndex, DistanceBand.MID, candidates, false);
    }

    private static List<SlotType> typesOf(List<TimeSlot> slots) {
        return slots.stream().map(TimeSlot::type).toList();
    }

    // ── 하루 리듬 기본 구조 ───────────────────────────────────────────────

    @Test
    void 평범한_하루는_다섯_슬롯이_리듬_순서대로_나온다() {
        // 활동 슬롯(오전/오후/저녁)은 셋 다 TOUR/CAFE 를 받을 수 있어 셋 중 어디로 갈지는
        // 거리에 따라 정해진다 — 여기서는 "카테고리가 안 섞이고 6개 다 소비된다"만 본다.
        List<Candidate> nonFood = List.of(
                alwaysOpen("act1", northOf(ANCHOR_POINT, 1), CandidateCategory.TOUR),
                alwaysOpen("act2", northOf(ANCHOR_POINT, 1.2), CandidateCategory.TOUR),
                alwaysOpen("act3", northOf(ANCHOR_POINT, 1.4), CandidateCategory.CAFE),
                alwaysOpen("act4", northOf(ANCHOR_POINT, 1.6), CandidateCategory.CAFE),
                alwaysOpen("act5", northOf(ANCHOR_POINT, 1.8), CandidateCategory.TOUR),
                alwaysOpen("act6", northOf(ANCHOR_POINT, 2.0), CandidateCategory.TOUR));
        List<Candidate> candidates = new java.util.ArrayList<>(nonFood);
        candidates.add(candidate("lunch1", northOf(ANCHOR_POINT, 1), CandidateCategory.FOOD,
                LocalTime.of(11, 0), LocalTime.of(14, 0)));
        candidates.add(candidate("lunch2", northOf(ANCHOR_POINT, 1.2), CandidateCategory.FOOD,
                LocalTime.of(11, 0), LocalTime.of(14, 0)));
        candidates.add(candidate("dinner1", northOf(ANCHOR_POINT, 1), CandidateCategory.FOOD,
                LocalTime.of(17, 0), LocalTime.of(20, 0)));
        candidates.add(candidate("dinner2", northOf(ANCHOR_POINT, 1.2), CandidateCategory.FOOD,
                LocalTime.of(17, 0), LocalTime.of(20, 0)));

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(poolOf(1, candidates), ANCHOR, constraints(null, null), 3);

        assertThat(typesOf(slots)).containsExactly(
                SlotType.MORNING_ACTIVITY, SlotType.LUNCH, SlotType.AFTERNOON_ACTIVITY,
                SlotType.DINNER, SlotType.EVENING_ACTIVITY);

        for (TimeSlot slot : slots) {
            boolean isMeal = slot.isMealSlot();
            assertThat(slot.candidates())
                    .allMatch(c -> isMeal == (c.category() == CandidateCategory.FOOD));
        }

        assertThat(slots.get(1).candidates()).extracting(Candidate::id)
                .containsExactlyInAnyOrder("lunch1", "lunch2");
        assertThat(slots.get(3).candidates()).extracting(Candidate::id)
                .containsExactlyInAnyOrder("dinner1", "dinner2");

        List<String> activityIds = new java.util.ArrayList<>();
        activityIds.addAll(slots.get(0).candidates().stream().map(Candidate::id).toList());
        activityIds.addAll(slots.get(2).candidates().stream().map(Candidate::id).toList());
        activityIds.addAll(slots.get(4).candidates().stream().map(Candidate::id).toList());
        assertThat(activityIds).containsExactlyInAnyOrder("act1", "act2", "act3", "act4", "act5", "act6");
    }

    @Test
    void 창은_있는데_후보가_없으면_빈_슬롯을_만든다() {
        List<Candidate> candidates = List.of(
                alwaysOpen("morning1", northOf(ANCHOR_POINT, 1), CandidateCategory.TOUR));

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(poolOf(1, candidates), ANCHOR, constraints(null, null), 3);

        assertThat(typesOf(slots)).containsExactly(
                SlotType.MORNING_ACTIVITY, SlotType.LUNCH, SlotType.AFTERNOON_ACTIVITY,
                SlotType.DINNER, SlotType.EVENING_ACTIVITY);
        assertThat(slots.get(0).size()).isEqualTo(1);
        assertThat(slots.get(1).size()).isEqualTo(0);
        assertThat(slots.get(2).size()).isEqualTo(0);
        assertThat(slots.get(3).size()).isEqualTo(0);
        assertThat(slots.get(4).size()).isEqualTo(0);
    }

    // ── 결정 7-1 : 첫날/마지막날 특례 ────────────────────────────────────

    @Test
    void 첫날_점심시간_이후_도착이면_MORNING_슬롯이_아예_없다() {
        LocalDateTime lateArrival = LocalDateTime.of(2026, 9, 21, 14, 0); // 점심창(11:30~13:30) 종료 이후
        List<Candidate> candidates = List.of(
                candidate("lunch1", northOf(ANCHOR_POINT, 1), CandidateCategory.FOOD,
                        LocalTime.of(11, 0), LocalTime.of(14, 0)),
                alwaysOpen("afternoon1", northOf(ANCHOR_POINT, 1), CandidateCategory.CAFE));

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(poolOf(0, candidates), ANCHOR, constraints(lateArrival, null), 3);

        assertThat(typesOf(slots)).containsExactly(
                SlotType.LUNCH, SlotType.AFTERNOON_ACTIVITY, SlotType.DINNER, SlotType.EVENING_ACTIVITY);
    }

    @Test
    void 마지막날_귀가시각이_이르면_EVENING_슬롯이_없다() {
        List<Candidate> candidates = List.of(alwaysOpen("morning1", northOf(ANCHOR_POINT, 1), CandidateCategory.TOUR));

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(
                poolOf(1, candidates), ANCHOR, constraints(null, LocalTime.of(19, 0)), 2);

        assertThat(typesOf(slots)).containsExactly(
                SlotType.MORNING_ACTIVITY, SlotType.LUNCH, SlotType.AFTERNOON_ACTIVITY, SlotType.DINNER);
    }

    // ── 결정 4 : 3단계 컷 ────────────────────────────────────────────────

    @Test
    void 카테고리_필터_영업시간_거리순_상위5_전부_적용된다() {
        List<Candidate> candidates = new java.util.ArrayList<>(List.of(
                alwaysOpen("tour1km", northOf(ANCHOR_POINT, 1), CandidateCategory.TOUR),
                alwaysOpen("tour2km", northOf(ANCHOR_POINT, 2), CandidateCategory.TOUR),
                alwaysOpen("tour3km", northOf(ANCHOR_POINT, 3), CandidateCategory.TOUR),
                alwaysOpen("tour4km", northOf(ANCHOR_POINT, 4), CandidateCategory.TOUR),
                alwaysOpen("tour5km", northOf(ANCHOR_POINT, 5), CandidateCategory.TOUR),
                alwaysOpen("tour6km", northOf(ANCHOR_POINT, 6), CandidateCategory.TOUR),
                alwaysOpen("tour7km", northOf(ANCHOR_POINT, 7), CandidateCategory.TOUR),
                // 제일 가깝지만 영업시간이 MORNING(09:00~11:30)과 안 겹친다
                candidate("closedNow", northOf(ANCHOR_POINT, 0.5), CandidateCategory.TOUR,
                        LocalTime.of(15, 0), LocalTime.of(16, 0)),
                // 제일 가깝지만 카테고리가 FOOD라 활동 슬롯에는 못 들어간다
                candidate("wrongCategory", northOf(ANCHOR_POINT, 0.3), CandidateCategory.FOOD,
                        LocalTime.of(9, 0), LocalTime.of(12, 0))));

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(poolOf(1, candidates), ANCHOR, constraints(null, null), 3);

        TimeSlot morning = slots.get(0);
        assertThat(morning.type()).isEqualTo(SlotType.MORNING_ACTIVITY);
        assertThat(morning.candidates()).extracting(Candidate::id).containsExactly(
                "tour1km", "tour2km", "tour3km", "tour4km", "tour5km");
    }

    @Test
    void filter를_통과하지_못하면_아무리_가까워도_제외된다() {
        List<Candidate> candidates = List.of(
                alwaysOpen("banned", northOf(ANCHOR_POINT, 0.5), CandidateCategory.TOUR),
                alwaysOpen("ok1", northOf(ANCHOR_POINT, 2), CandidateCategory.TOUR),
                alwaysOpen("ok2", northOf(ANCHOR_POINT, 3), CandidateCategory.TOUR));

        SlotBuilder builder = new SlotBuilder(
                c -> !c.id().equals("banned"), Haversine::distanceKm);
        List<TimeSlot> slots = builder.build(poolOf(1, candidates), ANCHOR, constraints(null, null), 3);

        assertThat(slots.get(0).candidates()).extracting(Candidate::id)
                .containsExactlyInAnyOrder("ok1", "ok2");
    }

    @Test
    void 한_슬롯에_배정된_후보는_다른_슬롯에_다시_나오지_않는다() {
        // 앵커에서 1~6km 북쪽, 6개. MORNING 이 가장 가까운 5개(1~5km)를 전부 가져가면
        // AFTERNOON 에는 6km 짜리 하나만 남아야 한다 — usedToday 가 없으면 5개가 다시 뜬다.
        List<Candidate> candidates = new java.util.ArrayList<>();
        for (int km = 1; km <= 6; km++) {
            candidates.add(alwaysOpen("c" + km, northOf(ANCHOR_POINT, km), CandidateCategory.TOUR));
        }

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(poolOf(1, candidates), ANCHOR, constraints(null, null), 3);

        assertThat(slots.get(0).candidates()).extracting(Candidate::id)
                .containsExactlyInAnyOrder("c1", "c2", "c3", "c4", "c5");
        assertThat(slots.get(2).candidates()).extracting(Candidate::id)
                .containsExactly("c6");
    }

    // ── 결정 7-2 : 기준점은 직전 슬롯 후보의 무게중심 ─────────────────────

    @Test
    void 기준점은_직전_슬롯_후보의_무게중심으로_옮겨간다() {
        // MORNING: 앵커에서 동쪽 5.0~5.4km, 5개 — 다른 후보는 전부 훨씬 멀어서 이 5개가 그대로 뽑힌다.
        // MORNING 무게중심 ≈ 동쪽 5.2km.
        List<Candidate> candidates = new java.util.ArrayList<>();
        double[] morningKm = {5.0, 5.1, 5.2, 5.3, 5.4};
        for (double km : morningKm) {
            candidates.add(alwaysOpen("morning-" + km, eastOf(ANCHOR_POINT, km), CandidateCategory.TOUR));
        }

        // near: 무게중심에서 ~9.8km. far: 무게중심에서 ~20.2km — 둘 다 앵커에서는 똑같이 15km 인
        // 것과 다르게, 무게중심 기준으로는 near 가 압도적으로 가깝다.
        double[] nearKm = {14.8, 14.9, 15.0, 15.1, 15.2};
        for (double km : nearKm) {
            candidates.add(alwaysOpen("near-" + km, eastOf(ANCHOR_POINT, km), CandidateCategory.CAFE));
        }
        double[] farKm = {14.8, 14.9, 15.0, 15.1, 15.2};
        for (double km : farKm) {
            candidates.add(alwaysOpen("far-" + km, eastOf(ANCHOR_POINT, -km), CandidateCategory.CAFE));
        }

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(poolOf(1, candidates), ANCHOR, constraints(null, null), 3);

        assertThat(slots.get(0).candidates()).extracting(Candidate::id)
                .allMatch(id -> id.startsWith("morning-"));

        TimeSlot afternoon = slots.get(2);
        assertThat(afternoon.candidates()).extracting(Candidate::id).allMatch(id -> id.startsWith("near-"));
    }

    // ── 당일치기 : 앵커가 없다 ────────────────────────────────────────────

    @Test
    void 앵커가_없으면_도착지점을_기준점으로_쓴다() {
        List<Candidate> candidates = List.of(
                alwaysOpen("nearArrival", northOf(ANCHOR_POINT, 1), CandidateCategory.TOUR));

        SlotBuilder builder = SlotBuilder.withDefaults();
        List<TimeSlot> slots = builder.build(poolOf(0, candidates), null, constraints(null, null), 1);

        assertThat(slots).isNotEmpty();
        assertThat(slots.get(0).candidates()).extracting(Candidate::id).containsExactly("nearArrival");
    }

    // ── 입력 검증 ───────────────────────────────────────────────────────

    @Test
    void filter가_없으면_예외() {
        assertThatThrownBy(() -> new SlotBuilder(null, Haversine::distanceKm))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void costMetric이_없으면_예외() {
        assertThatThrownBy(() -> new SlotBuilder(c -> true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pool이_없으면_예외() {
        SlotBuilder builder = SlotBuilder.withDefaults();
        assertThatThrownBy(() -> builder.build(null, ANCHOR, constraints(null, null), 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalDays가_1보다_작으면_예외() {
        SlotBuilder builder = SlotBuilder.withDefaults();
        assertThatThrownBy(() -> builder.build(poolOf(0, List.of()), ANCHOR, constraints(null, null), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dayIndex가_totalDays_범위_밖이면_예외() {
        SlotBuilder builder = SlotBuilder.withDefaults();
        assertThatThrownBy(() -> builder.build(poolOf(3, List.of()), ANCHOR, constraints(null, null), 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 점심이나_저녁_창이_없으면_예외() {
        RouteConstraints noLunch = new RouteConstraints(
                null, null, null, null,
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                null, ANCHOR_POINT, null, null, null);

        SlotBuilder builder = SlotBuilder.withDefaults();
        assertThatThrownBy(() -> builder.build(poolOf(0, List.of()), ANCHOR, noLunch, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
