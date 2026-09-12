package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
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
}
