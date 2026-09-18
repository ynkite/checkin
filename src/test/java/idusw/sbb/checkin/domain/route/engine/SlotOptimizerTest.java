package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotOptimizerTest {

    private static final GeoPoint ANCHOR_POINT = new GeoPoint(35.0, 129.0);

    private static GeoPoint northOf(GeoPoint origin, double km) {
        return new GeoPoint(origin.latitude() + km / 111.194, origin.longitude());
    }

    private static Candidate candidate(String id, GeoPoint location, CandidateCategory category,
                                        int dwellMinutes, LocalTime openTime, LocalTime closeTime) {
        return new Candidate(id, id, location, category, dwellMinutes, openTime, closeTime, null);
    }

    private static Candidate alwaysOpen(String id, GeoPoint location, int dwellMinutes) {
        return candidate(id, location, CandidateCategory.TOUR, dwellMinutes, null, null);
    }

    // ── 결정 8 : 방문 0곳도 정상 결과다 ──────────────────────────────────

    @Test
    void 창이_너무_좁으면_방문_0곳이_정상_결과다() {
        Candidate c = alwaysOpen("c1", northOf(ANCHOR_POINT, 100), 30); // 아주 멀어서 창 안에 못 들어옴
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(c));

        SlotOptimizer optimizer = SlotOptimizer.withDefaults();
        SlotPlan plan = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(9, 30), TimeSlot.MAX_CANDIDATES);

        assertThat(plan.visitCount()).isZero();
        assertThat(plan.endPoint()).isEqualTo(ANCHOR_POINT);
        assertThat(plan.endTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(plan.travelCost()).isZero();
    }

    // ── 결정 9-1 : LUNCH 는 maxVisits=1 이면 5개 중 한 곳만 ──────────────

    @Test
    void maxVisits가_1이면_5개여도_한_곳만_고른다() {
        List<Candidate> pool = new ArrayList<>();
        double[] km = {1, 2, 3, 4, 5};
        for (double d : km) {
            pool.add(candidate("food" + d, northOf(ANCHOR_POINT, d), CandidateCategory.FOOD, 60, null, null));
        }
        TimeSlot slot = new TimeSlot(SlotType.LUNCH, pool);

        SlotOptimizer optimizer = SlotOptimizer.withDefaults();
        SlotPlan plan = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(11, 30),
                LocalTime.of(11, 30), LocalTime.of(13, 30), 1);

        assertThat(plan.visitCount()).isEqualTo(1);
        assertThat(plan.visitOrder().get(0).id()).isEqualTo("food1.0"); // 가장 가까운 곳
    }

    // ── 창이 좁을 때 일부만 들어가고, 그 조합이 실제 최단인지 (브루트포스 검산) ──

    @Test
    void 창이_좁으면_일부만_들어가고_그_조합이_실제_최단이다() {
        // 앵커에서 5,10,15,20,25km 북쪽 — 이동만으로 시속 30km 가정 시 2/4/6/8/10분 남짓.
        List<Candidate> pool = new ArrayList<>();
        double[] km = {5, 10, 15, 20, 25};
        for (double d : km) {
            pool.add(alwaysOpen("t" + d, northOf(ANCHOR_POINT, d), 20)); // 체류 20분씩
        }
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, pool);

        LocalTime start = LocalTime.of(9, 0);
        LocalTime windowEnd = LocalTime.of(10, 30); // 90분짜리 창 — 5개 다는 못 들어간다

        SlotOptimizer optimizer = SlotOptimizer.withDefaults();
        SlotPlan actual = optimizer.optimize(slot, ANCHOR_POINT, start, start, windowEnd, TimeSlot.MAX_CANDIDATES);

        SlotPlan bruteForceBest = bruteForceBest(pool, ANCHOR_POINT, start, start, windowEnd);

        assertThat(actual.visitCount()).isEqualTo(bruteForceBest.visitCount());
        assertThat(actual.travelCost()).isCloseTo(bruteForceBest.travelCost(), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(actual.visitOrder()).extracting(Candidate::id)
                .containsExactlyElementsOf(bruteForceBest.visitOrder().stream().map(Candidate::id).toList());
    }

    /** SlotOptimizer 구현과 독립적으로 다시 짠 검산용 완전탐색 — 모든 순열(크기 무관)을 직접 돌려본다. */
    private SlotPlan bruteForceBest(List<Candidate> pool, GeoPoint startPoint, LocalTime startTime,
                                     LocalTime windowStart, LocalTime windowEnd) {
        List<List<Candidate>> allOrders = new ArrayList<>();
        permute(pool, new ArrayList<>(), new boolean[pool.size()], allOrders);

        SlotPlan best = SlotPlan.empty(SlotType.MORNING_ACTIVITY, startPoint, startTime);
        for (List<Candidate> order : allOrders) {
            for (int cut = 1; cut <= order.size(); cut++) {
                List<Candidate> attempt = order.subList(0, cut);
                GeoPoint point = startPoint;
                LocalTime time = startTime;
                double cost = 0.0;
                boolean feasible = true;
                for (int i = 0; i < attempt.size(); i++) {
                    Candidate c = attempt.get(i);
                    double leg = Haversine.distanceKm(point, c.location()) / 30.0 * 60.0;
                    LocalTime arrival = time.plusMinutes((long) Math.ceil(leg));
                    if (i == 0 && arrival.isBefore(windowStart)) {
                        arrival = windowStart;
                    }
                    if (arrival.isAfter(windowEnd)) {
                        feasible = false;
                        break;
                    }
                    cost += leg;
                    time = arrival.plusMinutes(c.dwellMinutes());
                    point = c.location();
                }
                if (!feasible) {
                    continue;
                }
                SlotPlan candidate = new SlotPlan(SlotType.MORNING_ACTIVITY, attempt, point, time, cost);
                if (candidate.visitCount() > best.visitCount()
                        || (candidate.visitCount() == best.visitCount() && candidate.travelCost() < best.travelCost())) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private void permute(List<Candidate> pool, List<Candidate> current, boolean[] used, List<List<Candidate>> out) {
        if (current.size() == pool.size()) {
            out.add(new ArrayList<>(current));
            return;
        }
        for (int i = 0; i < pool.size(); i++) {
            if (used[i]) {
                continue;
            }
            used[i] = true;
            current.add(pool.get(i));
            permute(pool, current, used, out);
            current.remove(current.size() - 1);
            used[i] = false;
        }
    }

    // ── 결정 8 : 동점 처리는 결정론적이다 ──────────────────────────────────

    @Test
    void 동점이면_후보_id_사전순으로_고르고_반복해도_같다() {
        // 앵커에서 정확히 같은 거리(동서 대칭) — 이동비용이 같아 진짜 동점이 된다.
        GeoPoint east = new GeoPoint(35.0, 129.05);
        GeoPoint west = new GeoPoint(35.0, 128.95);
        Candidate zCandidate = alwaysOpen("z", east, 30);
        Candidate aCandidate = alwaysOpen("a", west, 30);
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(zCandidate, aCandidate));

        SlotOptimizer optimizer = SlotOptimizer.withDefaults();
        SlotPlan first = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1);
        SlotPlan second = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1);

        assertThat(first).isEqualTo(second);
        assertThat(first.visitOrder().get(0).id()).isEqualTo("a"); // "a" < "z" 사전순
    }

    // ── 결정 9-3 : 창 시작 전 도착이면 기다린다 / 도착(끝) 기준으로만 탈락한다 ──

    @Test
    void 창이_열리기_전에_도착하면_창이_열릴때까지_기다린다() {
        Candidate c = candidate("c1", ANCHOR_POINT, CandidateCategory.FOOD, 60, null, null);
        TimeSlot slot = new TimeSlot(SlotType.LUNCH, List.of(c));

        SlotOptimizer optimizer = new SlotOptimizer((a, b) -> 5.0); // 이동 5분 고정
        SlotPlan plan = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(11, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 30), 1);

        // 도착은 11:05 지만 창 시작 12:00 까지 기다렸다가 60분 체류 → 13:00 종료
        assertThat(plan.endTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void 결정9_예시_11시50분_종료_25분_이동이면_점심은_12시15분_시작이다() {
        Candidate c = candidate("lunch1", ANCHOR_POINT, CandidateCategory.FOOD, 60, null, null);
        TimeSlot slot = new TimeSlot(SlotType.LUNCH, List.of(c));

        SlotOptimizer optimizer = new SlotOptimizer((a, b) -> 25.0); // 이동 25분 고정
        SlotPlan plan = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(11, 50),
                LocalTime.of(12, 0), LocalTime.of(13, 30), 1);

        // 11:50 + 25분 = 12:15 (창 시작 12:00 을 이미 넘겼으니 대기 없음) + 60분 체류 = 13:15
        assertThat(plan.endTime()).isEqualTo(LocalTime.of(13, 15));
    }

    @Test
    void 도착이_창_끝을_넘으면_그_후보는_탈락하고_다른_후보가_선택된다() {
        Candidate tooFar = alwaysOpen("far", northOf(ANCHOR_POINT, 50), 30); // 창을 넘긴다
        Candidate fits = alwaysOpen("near", northOf(ANCHOR_POINT, 1), 30);
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(tooFar, fits));

        SlotOptimizer optimizer = SlotOptimizer.withDefaults();
        SlotPlan plan = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(9, 20), TimeSlot.MAX_CANDIDATES);

        assertThat(plan.visitOrder()).extracting(Candidate::id).containsExactly("near");
    }

    @Test
    void 후보_자신의_영업시간_밖이면_탈락한다() {
        Candidate closed = candidate("closed", ANCHOR_POINT, CandidateCategory.TOUR, 30,
                LocalTime.of(15, 0), LocalTime.of(16, 0));
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(closed));

        SlotOptimizer optimizer = new SlotOptimizer((a, b) -> 0.0);
        SlotPlan plan = optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(11, 30), TimeSlot.MAX_CANDIDATES);

        assertThat(plan.visitCount()).isZero();
    }

    // ── 입력 검증 ───────────────────────────────────────────────────────

    @Test
    void travelTimeMinutes가_없으면_예외() {
        assertThatThrownBy(() -> new SlotOptimizer(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void averageSpeedKmh가_0이하면_예외() {
        assertThatThrownBy(() -> SlotOptimizer.withHaversineEstimate(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void windowStart가_windowEnd보다_늦으면_예외() {
        Candidate c = alwaysOpen("c1", ANCHOR_POINT, 30);
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(c));
        SlotOptimizer optimizer = SlotOptimizer.withDefaults();

        assertThatThrownBy(() -> optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(10, 0), LocalTime.of(9, 0), 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxVisits가_음수면_예외() {
        Candidate c = alwaysOpen("c1", ANCHOR_POINT, 30);
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(c));
        SlotOptimizer optimizer = SlotOptimizer.withDefaults();

        assertThatThrownBy(() -> optimizer.optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(10, 0), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 필수 포함 : 사용자가 직접 요청한 장소 (결정 13) ─────────────────

    @Test
    void 필수_후보를_포함하지_않는_조합은_버린다() {
        List<Candidate> pool = new ArrayList<>();
        for (double d : new double[]{1, 2, 3, 4, 5}) {
            pool.add(alwaysOpen("tour" + d, northOf(ANCHOR_POINT, d), 30));
        }
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, pool);

        // 방문 한 곳만 허용 — 제약이 없으면 가장 가까운 tour1.0 이 뽑힌다
        SlotPlan free = SlotOptimizer.withDefaults().optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(12, 0), 1);
        assertThat(free.visitOrder().get(0).id()).isEqualTo("tour1.0");

        SlotPlan required = SlotOptimizer.withDefaults().optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(12, 0), 1, Set.of("tour5.0"));
        assertThat(required.visitOrder()).extracting(Candidate::id).containsExactly("tour5.0");
    }

    @Test
    void 필수_후보가_여럿이면_전부_들어간_조합만_본다() {
        List<Candidate> pool = new ArrayList<>();
        for (double d : new double[]{1, 2, 3}) {
            pool.add(alwaysOpen("tour" + d, northOf(ANCHOR_POINT, d), 30));
        }
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, pool);

        SlotPlan plan = SlotOptimizer.withDefaults().optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(12, 0), 2, Set.of("tour2.0", "tour3.0"));

        assertThat(plan.visitOrder()).extracting(Candidate::id).containsExactlyInAnyOrder("tour2.0", "tour3.0");
    }

    @Test
    void 필수_후보를_넣을_수_있는_조합이_0개면_제약을_풀고_나머지로_채운다() {
        Candidate near = alwaysOpen("near", northOf(ANCHOR_POINT, 1), 30);
        Candidate unreachable = alwaysOpen("unreachable", northOf(ANCHOR_POINT, 300), 30); // 창 안에 못 들어옴
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of(near, unreachable));

        SlotPlan plan = SlotOptimizer.withDefaults().optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(10, 0), TimeSlot.MAX_CANDIDATES, Set.of("unreachable"));

        // 요청 장소 하나 때문에 슬롯을 통째로 비우지 않는다
        assertThat(plan.visitOrder()).extracting(Candidate::id).containsExactly("near");
    }

    @Test
    void 슬롯에_없는_필수_id는_아무_영향이_없다() {
        TimeSlot slot = new TimeSlot(SlotType.MORNING_ACTIVITY,
                List.of(alwaysOpen("tour1", northOf(ANCHOR_POINT, 1), 30)));

        SlotPlan plan = SlotOptimizer.withDefaults().optimize(slot, ANCHOR_POINT, LocalTime.of(9, 0),
                LocalTime.of(9, 0), LocalTime.of(12, 0), 5, Set.of("다른날에있는장소"));

        assertThat(plan.visitCount()).isEqualTo(1);
    }
}
