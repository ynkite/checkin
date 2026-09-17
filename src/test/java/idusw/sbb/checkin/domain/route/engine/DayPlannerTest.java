package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DayPlannerTest {

    private static final GeoPoint ANCHOR_POINT = new GeoPoint(35.0, 129.0);

    private static Candidate candidate(String id, GeoPoint location, CandidateCategory category, int dwellMinutes) {
        return new Candidate(id, id, location, category, dwellMinutes, null, null, null);
    }

    private static RouteConstraints constraints(LocalTime lunchStart, LocalTime lunchEnd,
                                                 LocalTime dinnerStart, LocalTime dinnerEnd,
                                                 LocalTime dayStart, LocalTime dayEnd,
                                                 Duration maxDailyTravelTime) {
        return new RouteConstraints(null, null, lunchStart, lunchEnd, dinnerStart, dinnerEnd,
                maxDailyTravelTime, ANCHOR_POINT, null, dayStart, dayEnd);
    }

    /** 위도 1도 = 111.194km. 전부 앵커에서 1~2km 안이라 이동시간이 제약으로 걸리지 않는다. */
    private static GeoPoint nearby(double km) {
        return new GeoPoint(ANCHOR_POINT.latitude() + km / 111.194, ANCHOR_POINT.longitude());
    }

    /** 하루 다섯 슬롯 — 활동 슬롯마다 관광 후보 2곳씩, 식사 슬롯마다 식당 1곳씩. */
    private static List<TimeSlot> fullDaySlots() {
        return List.of(
                new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(
                        candidate("tour-morning-1", nearby(1.0), CandidateCategory.TOUR, 90),
                        candidate("tour-morning-2", nearby(1.1), CandidateCategory.TOUR, 90))),
                new TimeSlot(SlotType.LUNCH, List.of(
                        candidate("food-lunch", nearby(1.2), CandidateCategory.FOOD, 60))),
                new TimeSlot(SlotType.AFTERNOON_ACTIVITY, List.of(
                        candidate("tour-afternoon-1", nearby(1.3), CandidateCategory.TOUR, 90),
                        candidate("tour-afternoon-2", nearby(1.4), CandidateCategory.TOUR, 90))),
                new TimeSlot(SlotType.DINNER, List.of(
                        candidate("food-dinner", nearby(1.5), CandidateCategory.FOOD, 60))),
                new TimeSlot(SlotType.EVENING_ACTIVITY, List.of(
                        candidate("tour-evening-1", nearby(1.6), CandidateCategory.TOUR, 90),
                        candidate("tour-evening-2", nearby(1.7), CandidateCategory.TOUR, 90))));
    }

    /**
     * 활동 슬롯 셋만. 후보는 체류 10분짜리로 촘촘히 둬서 <b>시간이 아니라 예산이</b> 방문 수를
     * 정하게 한다 — 몫 계산만 보려는 것이다.
     */
    private static List<TimeSlot> activityOnlySlots(int perSlot) {
        return List.of(
                cheapSlot(SlotType.MORNING_ACTIVITY, "morning", perSlot),
                cheapSlot(SlotType.AFTERNOON_ACTIVITY, "afternoon", perSlot),
                cheapSlot(SlotType.EVENING_ACTIVITY, "evening", perSlot));
    }

    private static TimeSlot cheapSlot(SlotType type, String prefix, int count) {
        List<Candidate> candidates = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            candidates.add(candidate(prefix + "-" + k, nearby(0.1 * (k + 1)), CandidateCategory.TOUR, 10));
        }
        return new TimeSlot(type, candidates);
    }

    private static RouteConstraints fullDayConstraints() {
        return constraints(LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                LocalTime.of(9, 0), LocalTime.of(21, 0), null);
    }

    private static List<Integer> visitCounts(DayPlan plan) {
        return plan.slotPlans().stream().map(SlotPlan::visitCount).toList();
    }

    // ── 결정 11-(1) : 하루 카테고리 예산을 활동 슬롯에 고르게 나눈다 ──────────

    @Test
    void 관광_예산이_2면_오전1_오후1_저녁0_으로_갈린다() {
        DayPlan plan = DayPlanner.withDefaults().plan(fullDaySlots(), ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 2));

        // [MORNING, LUNCH, AFTERNOON, DINNER, EVENING]
        assertThat(visitCounts(plan)).containsExactly(1, 1, 1, 1, 0);
    }

    @Test
    void 관광_예산이_3이면_창이_긴_오후가_두_곳을_가져간다() {
        // 창은 오전 3h(09:00~12:00) · 오후 4.5h(13:30~18:00) · 저녁 1.5h(19:30~21:00) = 9h.
        // 3 × (3/9, 4.5/9, 1.5/9) = 1.0, 1.5, 0.5 → 내림 1·1·0, 잔여 1은 나머지가 같으면 창이 긴 오후로.
        DayPlan plan = DayPlanner.withDefaults().plan(fullDaySlots(), ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 3));

        assertThat(visitCounts(plan)).containsExactly(1, 1, 2, 1, 0);
    }

    @Test
    void 활동_예산이_1이면_가장_긴_창_하나만_가져간다() {
        DayPlan plan = DayPlanner.withDefaults().plan(activityOnlySlots(5), ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 1));

        assertThat(visitCounts(plan)).containsExactly(0, 1, 0); // 오후(4.5h)만
    }

    @Test
    void 오전_창이_없는_첫날은_오후와_저녁으로_몰린다() {
        // 12:00 도착 → 오전 창이 [12:00, 12:00] 으로 접힌다. 창 길이 0 이라 몫도 0 이다.
        RouteConstraints lateArrival = new RouteConstraints(
                LocalDateTime.of(2026, 9, 19, 12, 0), null,
                LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                null, ANCHOR_POINT, null, LocalTime.of(9, 0), LocalTime.of(21, 0));

        DayPlan plan = DayPlanner.withDefaults().plan(activityOnlySlots(5), ANCHOR_POINT, ANCHOR_POINT,
                lateArrival, 0, 3, new DailyCategoryBudget(3, 0, 4));

        assertThat(plan.slotPlans().get(0).visitCount()).isZero();
        assertThat(visitCounts(plan).stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
    }

    @Test
    void 몫의_합은_언제나_활동_예산과_같다() {
        for (int budget = 1; budget <= 6; budget++) {
            DayPlan plan = DayPlanner.withDefaults().plan(activityOnlySlots(5), ANCHOR_POINT, ANCHOR_POINT,
                    fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, budget));

            assertThat(visitCounts(plan).stream().mapToInt(Integer::intValue).sum())
                    .as("활동 예산 " + budget)
                    .isEqualTo(budget);
        }
    }

    @Test
    void 예산을_앞_슬롯이_다_쓰지_않는다() {
        // 앞에서부터 상한껏 쓰면 오전 2 · 오후 0 · 저녁 0 이 된다 — before 데이터의 그 모양이다.
        DayPlan plan = DayPlanner.withDefaults().plan(fullDaySlots(), ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 2));

        assertThat(plan.slotPlans().get(0).visitCount()).isEqualTo(1);
    }

    @Test
    void 저녁_활동이_0인_날은_저녁식사로_닫힌다() {
        DayPlan plan = DayPlanner.withDefaults().plan(fullDaySlots(), ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 2));

        SlotPlan dinner = plan.slotPlans().get(3);
        SlotPlan evening = plan.slotPlans().get(4);

        assertThat(dinner.visitCount()).isEqualTo(1);
        assertThat(evening.visitCount()).isZero();
        // 빈 저녁 슬롯은 저녁식사의 종료 지점·시각을 그대로 물려받고, 복귀는 거기서 계산된다
        assertThat(evening.endPoint()).isEqualTo(dinner.visitOrder().get(0).location());
        assertThat(evening.endTime()).isEqualTo(dinner.endTime());
        assertThat(plan.returnTime()).isAfter(dinner.endTime());
    }

    @Test
    void 예산이_0인_카테고리_후보는_슬롯에_들어가지_않는다() {
        List<TimeSlot> slots = List.of(
                new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(
                        candidate("cafe-morning", nearby(1.0), CandidateCategory.CAFE, 40),
                        candidate("tour-morning", nearby(1.1), CandidateCategory.TOUR, 90))),
                new TimeSlot(SlotType.AFTERNOON_ACTIVITY, List.of(
                        candidate("cafe-afternoon", nearby(1.2), CandidateCategory.CAFE, 40))));

        DayPlan plan = DayPlanner.withDefaults().plan(slots, ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 5));

        assertThat(plan.slotPlans()).flatExtracting(SlotPlan::visitOrder)
                .extracting(Candidate::category)
                .containsOnly(CandidateCategory.TOUR);
        assertThat(plan.slotPlans().get(1).visitCount()).isZero(); // 카페뿐인 슬롯은 통째로 빈다
    }

    @Test
    void 식사_슬롯_상한은_예산과_무관하게_한_곳이다() {
        List<TimeSlot> slots = List.of(new TimeSlot(SlotType.LUNCH, List.of(
                candidate("food1", nearby(1.0), CandidateCategory.FOOD, 60),
                candidate("food2", nearby(1.1), CandidateCategory.FOOD, 60),
                candidate("food3", nearby(1.2), CandidateCategory.FOOD, 60))));

        DayPlan plan = DayPlanner.withDefaults().plan(slots, ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 1, 2));

        assertThat(plan.slotPlans().get(0).visitCount()).isEqualTo(1);
    }

    @Test
    void 밀도에서_예산을_그대로_가져온다() {
        assertThat(DailyCategoryBudget.of(ScheduleDensity.RELAXED))
                .isEqualTo(new DailyCategoryBudget(3, 1, 1));
        assertThat(DailyCategoryBudget.of(ScheduleDensity.of("빼곡하게")))
                .isEqualTo(new DailyCategoryBudget(3, 1, 2));
    }

    @Test
    void 예산을_안_주면_예전처럼_시간만_자른다() {
        List<TimeSlot> slots = fullDaySlots();
        RouteConstraints c = fullDayConstraints();

        DayPlan withoutBudget = DayPlanner.withDefaults().plan(slots, ANCHOR_POINT, ANCHOR_POINT, c, 0, 3);
        DayPlan unlimited = DayPlanner.withDefaults().plan(slots, ANCHOR_POINT, ANCHOR_POINT, c, 0, 3,
                DailyCategoryBudget.unlimited());

        assertThat(visitCounts(withoutBudget)).isEqualTo(visitCounts(unlimited));
        assertThat(visitCounts(withoutBudget).stream().mapToInt(Integer::intValue).sum())
                .isGreaterThan(5); // 예산이 없으면 활동 슬롯이 후보를 더 담는다
    }

    // ── 결정 9-3 : 경계 이동이 뒤 슬롯 창을 먹는다 ──────────────────────────

    @Test
    void 결정9_예시_11시50분_종료_25분_이동이면_점심은_12시15분_시작이다() {
        GeoPoint morningSpot = new GeoPoint(35.1, 129.0);
        GeoPoint lunchSpot = new GeoPoint(35.2, 129.0);

        // 앵커→오전장소 20분, 오전장소→점심장소 25분 — 그 외 호출은 이 테스트에서 안 일어난다.
        ToDoubleBiFunction<GeoPoint, GeoPoint> travel = (from, to) -> {
            if (from.equals(ANCHOR_POINT) && to.equals(morningSpot)) {
                return 20.0;
            }
            if (from.equals(morningSpot) && to.equals(lunchSpot)) {
                return 25.0;
            }
            if (from.equals(lunchSpot) && to.equals(ANCHOR_POINT)) {
                return 30.0; // 마지막 슬롯의 복귀 구간 — 이 테스트의 관심사는 아니다
            }
            throw new IllegalStateException("예상 밖 구간: " + from + " -> " + to);
        };

        // 오전 장소 체류 150분: 09:00 도착(+20분=09:20) + 150분 체류 = 11:50 종료
        List<TimeSlot> slots = List.of(
                new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(
                        candidate("morning1", morningSpot, CandidateCategory.TOUR, 150))),
                new TimeSlot(SlotType.LUNCH, List.of(
                        candidate("lunch1", lunchSpot, CandidateCategory.FOOD, 60))));

        RouteConstraints c = constraints(
                LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                LocalTime.of(9, 0), LocalTime.of(21, 0), null);

        DayPlanner planner = new DayPlanner(new SlotOptimizer(travel), travel, DayPlanner::defaultMaxVisits);
        DayPlan plan = planner.plan(slots, ANCHOR_POINT, ANCHOR_POINT, c, 0, 3);

        SlotPlan morning = plan.slotPlans().get(0);
        SlotPlan lunch = plan.slotPlans().get(1);

        assertThat(morning.endTime()).isEqualTo(LocalTime.of(11, 50));
        // 11:50 + 25분 = 12:15 (점심창 시작 12:00 을 이미 넘겼으니 대기 없음) + 60분 체류 = 13:15
        assertThat(lunch.endTime()).isEqualTo(LocalTime.of(13, 15));
    }

    // ── 결정 9-1 : DayPlanner 가 슬롯 타입별 maxVisits 를 적용한다 ──────────

    @Test
    void LUNCH_슬롯은_후보_5개여도_DayPlanner_레벨에서도_한_곳뿐이다() {
        java.util.List<Candidate> lunchCandidates = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            lunchCandidates.add(candidate("food" + i, new GeoPoint(35.0 + i * 0.01, 129.0), CandidateCategory.FOOD, 60));
        }
        List<TimeSlot> slots = List.of(new TimeSlot(SlotType.LUNCH, lunchCandidates));

        RouteConstraints c = constraints(
                LocalTime.of(11, 30), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                LocalTime.of(9, 0), LocalTime.of(21, 0), null);

        DayPlanner planner = DayPlanner.withDefaults();
        DayPlan plan = planner.plan(slots, ANCHOR_POINT, ANCHOR_POINT, c, 0, 1);

        assertThat(plan.slotPlans().get(0).visitCount()).isEqualTo(1);
    }

    // ── 결정 9-4 : 상한 위반은 마지막에 확정한 슬롯부터 줄인다 ────────────────

    @Test
    void maxDailyTravelTime을_넘기면_마지막_슬롯만_줄고_앞_슬롯은_그대로다() {
        GeoPoint morningSpot = new GeoPoint(35.1, 129.0);
        GeoPoint evening1 = new GeoPoint(35.2, 129.0);
        GeoPoint evening2 = new GeoPoint(35.2, 129.01);
        GeoPoint evening3 = new GeoPoint(35.2, 129.02);
        GeoPoint returnPoint = ANCHOR_POINT;

        // 앵커→오전장소만 10분, 그 외(저녁 슬롯 내부·저녁→복귀 포함)는 전부 50분 고정.
        ToDoubleBiFunction<GeoPoint, GeoPoint> travel = (from, to) ->
                (from.equals(ANCHOR_POINT) && to.equals(morningSpot)) ? 10.0 : 50.0;

        List<TimeSlot> slots = List.of(
                new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(
                        candidate("morning1", morningSpot, CandidateCategory.TOUR, 10))),
                new TimeSlot(SlotType.EVENING_ACTIVITY, List.of(
                        candidate("evening1", evening1, CandidateCategory.TOUR, 10),
                        candidate("evening2", evening2, CandidateCategory.TOUR, 10),
                        candidate("evening3", evening3, CandidateCategory.TOUR, 10))));

        RouteConstraints c = constraints(
                LocalTime.of(12, 0), LocalTime.of(12, 30),
                LocalTime.of(9, 55), LocalTime.of(10, 0), // EVENING 창 시작(=이 값) 을 앞당기려는 목적, 실제 시간대는 안 중요
                LocalTime.of(9, 0), LocalTime.of(23, 59),
                Duration.ofMinutes(150)); // 오전(10) + 저녁 자연결과(150) + 복귀(50) = 210 > 150

        DayPlanner planner = new DayPlanner(new SlotOptimizer(travel), travel, DayPlanner::defaultMaxVisits);
        DayPlan plan = planner.plan(slots, ANCHOR_POINT, returnPoint, c, 1, 3);

        SlotPlan morning = plan.slotPlans().get(0);
        SlotPlan evening = plan.slotPlans().get(1);

        assertThat(morning.visitCount()).isEqualTo(1); // 앞 슬롯은 안 건드린다
        assertThat(morning.travelCost()).isEqualTo(10.0);
        assertThat(evening.visitCount()).isEqualTo(1); // 자연결과 3곳에서 줄었다
    }

    @Test
    void 복귀_거리_때문에만_상한을_넘기면_마지막_슬롯이_비워진다() {
        // 슬롯 자체 방문(50분)은 예산(100분) 안에 들어오지만, 복귀(60분)를 더하면 110 > 100.
        GeoPoint candidateSpot = new GeoPoint(35.1, 129.0);
        GeoPoint returnPoint = new GeoPoint(35.2, 129.0);

        ToDoubleBiFunction<GeoPoint, GeoPoint> travel = (from, to) -> {
            if (from.equals(ANCHOR_POINT) && to.equals(candidateSpot)) {
                return 50.0;
            }
            if (from.equals(candidateSpot) && to.equals(returnPoint)) {
                return 60.0;
            }
            if (from.equals(ANCHOR_POINT) && to.equals(returnPoint)) {
                return 5.0; // maxVisits=0(방문 없음)으로 줄었을 때의 복귀 구간
            }
            throw new IllegalStateException("예상 밖 구간: " + from + " -> " + to);
        };

        List<TimeSlot> slots = List.of(new TimeSlot(SlotType.LUNCH, List.of(
                candidate("lunch1", candidateSpot, CandidateCategory.FOOD, 30))));

        RouteConstraints c = constraints(
                LocalTime.of(11, 30), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                LocalTime.of(9, 0), LocalTime.of(21, 0),
                Duration.ofMinutes(100));

        DayPlanner planner = new DayPlanner(new SlotOptimizer(travel), travel, DayPlanner::defaultMaxVisits);
        DayPlan plan = planner.plan(slots, ANCHOR_POINT, returnPoint, c, 0, 1);

        // 방문(50)만 보면 예산 안이지만 복귀(60)까지 합치면 넘친다 — 복귀가 누적에 안 더해지는
        // 버그라면 이 슬롯은 그대로 1곳 방문으로 남는다.
        assertThat(plan.slotPlans().get(0).visitCount()).isZero();
    }

    @Test
    void maxDailyTravelTime이_없으면_줄이지_않는다() {
        List<TimeSlot> slots = List.of(new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(
                candidate("m1", new GeoPoint(35.01, 129.0), CandidateCategory.TOUR, 30))));

        RouteConstraints c = constraints(
                LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                LocalTime.of(9, 0), LocalTime.of(21, 0), null);

        DayPlanner planner = DayPlanner.withDefaults();
        DayPlan plan = planner.plan(slots, ANCHOR_POINT, ANCHOR_POINT, c, 0, 1);

        assertThat(plan.slotPlans().get(0).visitCount()).isEqualTo(1);
    }

    // ── 기본 동작 ───────────────────────────────────────────────────────

    @Test
    void 빈_슬롯_목록이면_바로_복귀_시각을_계산한다() {
        RouteConstraints c = constraints(
                LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                LocalTime.of(9, 0), LocalTime.of(21, 0), null);

        GeoPoint returnPoint = new GeoPoint(35.01, 129.0);
        DayPlanner planner = DayPlanner.withDefaults();
        DayPlan plan = planner.plan(List.of(), ANCHOR_POINT, returnPoint, c, 0, 1);

        assertThat(plan.slotPlans()).isEmpty();
        assertThat(plan.returnTime()).isAfter(LocalTime.of(9, 0));
    }

    // ── 입력 검증 ───────────────────────────────────────────────────────

    @Test
    void slotOptimizer가_없으면_예외() {
        assertThatThrownBy(() -> new DayPlanner(null, Haversine::distanceKm, DayPlanner::defaultMaxVisits))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalDays보다_dayIndex가_크거나_같으면_예외() {
        RouteConstraints c = constraints(
                LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                LocalTime.of(9, 0), LocalTime.of(21, 0), null);
        DayPlanner planner = DayPlanner.withDefaults();

        assertThatThrownBy(() -> planner.plan(List.of(), ANCHOR_POINT, ANCHOR_POINT, c, 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 필수 포함은 예산·상한보다 우선한다 (결정 13) ─────────────────────

    @Test
    void 예산이_0인_카테고리라도_필수_후보는_들어간다() {
        List<TimeSlot> slots = List.of(new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(
                candidate("요청카페", nearby(1.0), CandidateCategory.CAFE, 40),
                candidate("tour1", nearby(1.1), CandidateCategory.TOUR, 90))));

        DayPlan withoutRequirement = DayPlanner.withDefaults().plan(slots, ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 5));
        assertThat(withoutRequirement.slotPlans().get(0).visitOrder())
                .extracting(Candidate::id).doesNotContain("요청카페");

        DayPlan withRequirement = DayPlanner.withDefaults().plan(slots, ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 5), java.util.Set.of("요청카페"));
        assertThat(withRequirement.slotPlans().get(0).visitOrder())
                .extracting(Candidate::id).contains("요청카페");
    }

    @Test
    void 활동_몫이_0인_슬롯이어도_필수_후보는_들어간다() {
        // 관광 예산 1 → 창이 가장 긴 오후만 몫을 갖고 오전·저녁은 0 이다
        List<TimeSlot> slots = activityOnlySlots(2);
        String requiredId = slots.get(0).candidates().get(0).id();

        DayPlan plan = DayPlanner.withDefaults().plan(slots, ANCHOR_POINT, ANCHOR_POINT,
                fullDayConstraints(), 0, 3, new DailyCategoryBudget(3, 0, 1), java.util.Set.of(requiredId));

        assertThat(plan.slotPlans().get(0).visitOrder()).extracting(Candidate::id).contains(requiredId);
    }
}
