package idusw.sbb.checkin.domain.route.engine;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;

/**
 * 하루치 슬롯들({@link SlotBuilder} 산출물)을 순서대로 이어 붙여 {@link SlotOptimizer} 를
 * 반복 호출하고, 마지막 슬롯을 숙소(또는 귀가 지점) 복귀로 닫는다
 * ({@code ..\동선엔진_작업브리핑.md} 결정 6·8·9).
 *
 * <p>슬롯 B 는 슬롯 A 의 종료 지점·종료 시각을 그대로 이어받는다 — "도착하는 쪽이
 * 이동시간을 부담한다"(결정 9-3). 창을 넘기는 실제 판정은 {@link SlotOptimizer} 안에서
 * 일어난다; 여기서는 그 결과(종료 지점·시각·비용)만 누적한다.
 *
 * <p>슬롯별 최대 방문 수는 {@code maxVisitsPolicy} 로 결정한다 — 기본은 결정 9-1의
 * 표(식사=1, 활동=무제한)다. 나중에 "알차게/느긋하게" 를 LLM 이 정하게 되면 이 정책만
 * 바꿔 끼우면 된다 — 지금은 그 자리만 남겨둔다(결정 8·9).
 *
 * <p>{@code maxDailyTravelTime} 상한 위반은 **마지막에 확정한 슬롯부터** 줄인다 —
 * {@code maxVisits} 를 하나씩 낮춰 그 슬롯만 다시 최적화하고, 1까지 줄여도 넘으면 그
 * 슬롯은 비운다. 이미 확정된 앞 슬롯은 다시 풀지 않는다(결정 9-4). 하루 마지막 슬롯의
 * 복귀 이동도 이 상한에 포함된다.
 */
public final class DayPlanner {

    private final SlotOptimizer slotOptimizer;
    private final ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes;
    private final Function<SlotType, Integer> maxVisitsPolicy;

    public DayPlanner(SlotOptimizer slotOptimizer, ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes,
                       Function<SlotType, Integer> maxVisitsPolicy) {
        if (slotOptimizer == null) {
            throw new IllegalArgumentException("slotOptimizer must not be null");
        }
        if (travelTimeMinutes == null) {
            throw new IllegalArgumentException("travelTimeMinutes must not be null");
        }
        if (maxVisitsPolicy == null) {
            throw new IllegalArgumentException("maxVisitsPolicy must not be null");
        }
        this.slotOptimizer = slotOptimizer;
        this.travelTimeMinutes = travelTimeMinutes;
        this.maxVisitsPolicy = maxVisitsPolicy;
    }

    /** 결정 9-1 표(식사=1곳, 활동=무제한) · averageSpeedKmh=30 · Haversine 비용 — 기본값 그대로. */
    public static DayPlanner withDefaults() {
        return withCostMetric(TravelCostMetric.haversineDefault());
    }

    /** 이동비용 함수 하나로 {@link SlotOptimizer} 까지 같이 묶는다 — 둘이 다른 함수를 쓰면 안 된다. */
    public static DayPlanner withCostMetric(ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes) {
        return new DayPlanner(new SlotOptimizer(travelTimeMinutes), travelTimeMinutes, DayPlanner::defaultMaxVisits);
    }

    /** 결정 9-1 표: 식사 슬롯은 1곳, 활동 슬롯은 시간이 알아서 자르니 무제한(TimeSlot 상한까지). */
    public static int defaultMaxVisits(SlotType type) {
        return (type == SlotType.LUNCH || type == SlotType.DINNER) ? 1 : TimeSlot.MAX_CANDIDATES;
    }

    /**
     * @param slots       그 날의 슬롯들, 하루 리듬 순서대로 (SlotBuilder 산출물)
     * @param startPoint  하루 시작 지점 (앵커, 없으면 도착 지점)
     * @param returnPoint 하루를 닫는 복귀 지점 (보통 앵커 — 마지막 날은 귀가 지점일 수 있다)
     * @param constraints 점심/저녁 창, dayStartTime/dayEndTime, maxDailyTravelTime 등
     * @param dayIndex    0부터 시작하는 일자 번호
     * @param totalDays   여행 일수
     */
    public DayPlan plan(List<TimeSlot> slots, GeoPoint startPoint, GeoPoint returnPoint,
                         RouteConstraints constraints, int dayIndex, int totalDays) {
        return plan(slots, startPoint, returnPoint, constraints, dayIndex, totalDays,
                DailyCategoryBudget.unlimited());
    }

    /**
     * @param budget 하루 카테고리 예산 (결정 11-(1)). 활동 슬롯은 <b>창 길이에 비례해</b> 몫을
     *               나눠 갖는다 — 앞에서부터 다 쓰면 오전에 몰리고, 균등하게 쪼개면 창이 긴 오후가
     *               헐거워진다. 예산이 0인 카테고리는 슬롯에 들어가기 전에 후보 풀에서 빠진다.
     */
    public DayPlan plan(List<TimeSlot> slots, GeoPoint startPoint, GeoPoint returnPoint,
                         RouteConstraints constraints, int dayIndex, int totalDays,
                         DailyCategoryBudget budget) {
        return plan(slots, startPoint, returnPoint, constraints, dayIndex, totalDays, budget, Set.of());
    }

    /**
     * @param requiredIds 반드시 포함해야 하는 후보 id. 예산이 0인 카테고리라도 풀에서 빼지 않고,
     *                    슬롯 상한도 필수 후보 수까지는 올린다 — 예산·상한보다 우선한다.
     */
    public DayPlan plan(List<TimeSlot> slots, GeoPoint startPoint, GeoPoint returnPoint,
                         RouteConstraints constraints, int dayIndex, int totalDays,
                         DailyCategoryBudget budget, Set<String> requiredIds) {
        if (budget == null) {
            throw new IllegalArgumentException("budget must not be null");
        }
        if (slots == null) {
            throw new IllegalArgumentException("slots must not be null");
        }
        if (startPoint == null || returnPoint == null) {
            throw new IllegalArgumentException("startPoint/returnPoint must not be null");
        }
        if (constraints == null) {
            throw new IllegalArgumentException("constraints must not be null");
        }
        if (totalDays < 1) {
            throw new IllegalArgumentException("totalDays must be at least 1");
        }
        if (dayIndex < 0 || dayIndex >= totalDays) {
            throw new IllegalArgumentException("dayIndex must be within [0, totalDays)");
        }

        boolean isFirstDay = dayIndex == 0;
        boolean isLastDay = dayIndex == totalDays - 1;
        Duration travelBudget = constraints.maxDailyTravelTime();
        double budgetMinutes = travelBudget != null ? travelBudget.toSeconds() / 60.0 : Double.MAX_VALUE;

        GeoPoint currentPoint = startPoint;
        LocalTime currentTime = constraints.effectiveDayStart(isFirstDay);
        double cumulativeCost = 0.0;
        List<SlotPlan> results = new ArrayList<>();

        Map<CandidateCategory, Integer> remaining = budget.toRemaining();
        int[] activityQuota = activityQuotas(slots, constraints, budget, isFirstDay, isLastDay);

        for (int i = 0; i < slots.size(); i++) {
            TimeSlot slot = affordable(slots.get(i), remaining, requiredIds);
            boolean isLastSlot = i == slots.size() - 1;
            LocalTime windowStart = constraints.windowStart(slot.type(), isFirstDay);
            LocalTime windowEnd = constraints.windowEnd(slot.type(), isLastDay);
            int cap = maxVisitsPolicy.apply(slot.type());
            if (activityQuota[i] >= 0) {
                cap = Math.min(cap, activityQuota[i]);
            }
            cap = Math.max(cap, (int) slot.candidates().stream()
                    .filter(c -> requiredIds.contains(c.id())).count());

            SlotPlan accepted = null;
            double acceptedReturnLeg = 0.0;

            for (int maxVisits = cap; maxVisits >= 0; maxVisits--) {
                SlotPlan candidate = slotOptimizer.optimize(
                        slot, currentPoint, currentTime, windowStart, windowEnd, maxVisits, requiredIds);
                double returnLeg = isLastSlot
                        ? travelTimeMinutes.applyAsDouble(candidate.endPoint(), returnPoint)
                        : 0.0;
                boolean fitsBudget = cumulativeCost + candidate.travelCost() + returnLeg <= budgetMinutes;

                if (fitsBudget || maxVisits == 0) {
                    accepted = candidate;
                    acceptedReturnLeg = returnLeg;
                    break;
                }
            }

            results.add(accepted);
            for (Candidate visited : accepted.visitOrder()) {
                remaining.merge(visited.category(), -1, Integer::sum);
            }
            cumulativeCost += accepted.travelCost();
            currentPoint = accepted.endPoint();
            currentTime = accepted.endTime();

            if (isLastSlot) {
                cumulativeCost += acceptedReturnLeg;
                currentTime = currentTime.plusMinutes((long) Math.ceil(acceptedReturnLeg));
                currentPoint = returnPoint;
            }
        }

        LocalTime returnTime = slots.isEmpty()
                ? currentTime.plusMinutes((long) Math.ceil(travelTimeMinutes.applyAsDouble(currentPoint, returnPoint)))
                : currentTime;

        return new DayPlan(dayIndex, results, returnTime);
    }

    private static boolean isActivitySlot(SlotType type) {
        return type == SlotType.MORNING_ACTIVITY
                || type == SlotType.AFTERNOON_ACTIVITY
                || type == SlotType.EVENING_ACTIVITY;
    }

    /**
     * 활동 슬롯별 몫 = 활동 예산 × (그 슬롯 창 길이 / 활동 창 길이 합), 내림 후 나머지가 큰 순서로
     * 잔여를 하나씩 얹는다(최대잔여법). 몫의 합은 예산과 정확히 같다.
     *
     * <p>균등 분배는 앞 몰아쓰기는 막지만 창이 긴 오후가 헐거워진다 — 4시간 30분짜리 창에 90분
     * 관광 하나만 들어가면 3시간이 뜬다. before 데이터의 강릉 Day 3 이 그 모양이었다.
     *
     * @return 슬롯 위치별 몫. 활동 슬롯이 아니거나 예산이 무제한이면 -1 (상한 없음)
     */
    private static int[] activityQuotas(List<TimeSlot> slots, RouteConstraints constraints,
                                         DailyCategoryBudget budget, boolean isFirstDay, boolean isLastDay) {
        int[] quotas = new int[slots.size()];
        Arrays.fill(quotas, -1);
        if (budget.isUnlimited()) {
            return quotas;
        }

        long[] windows = new long[slots.size()];
        long totalWindow = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (!isActivitySlot(slots.get(i).type())) {
                continue;
            }
            quotas[i] = 0;
            windows[i] = Math.max(0, Duration.between(
                    constraints.windowStart(slots.get(i).type(), isFirstDay),
                    constraints.windowEnd(slots.get(i).type(), isLastDay)).toMinutes());
            totalWindow += windows[i];
        }
        if (totalWindow == 0) {
            return quotas; // 활동 창이 하나도 안 열린 날 — 전부 0
        }

        final long windowSum = totalWindow;
        final int activityBudget = budget.activityTotal();
        int assigned = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (quotas[i] < 0) {
                continue;
            }
            quotas[i] = (int) (activityBudget * windows[i] / windowSum);
            assigned += quotas[i];
        }

        // 잔여는 나머지가 큰 순서로. 나머지가 같으면 창이 긴 쪽, 그래도 같으면 앞 슬롯.
        List<Integer> byRemainder = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (quotas[i] >= 0) {
                byRemainder.add(i);
            }
        }
        byRemainder.sort(Comparator
                .comparingLong((Integer i) -> (long) activityBudget * windows[i] % windowSum).reversed()
                .thenComparing(Comparator.comparingLong((Integer i) -> windows[i]).reversed())
                .thenComparingInt(i -> i));

        for (int i = 0; i < byRemainder.size() && assigned < activityBudget; i++) {
            quotas[byRemainder.get(i)]++;
            assigned++;
        }
        return quotas;
    }

    /** 예산이 0인 카테고리 후보는 슬롯에 들어가기 전에 뺀다 — 단 필수 후보는 예산과 무관하게 남긴다. */
    private static TimeSlot affordable(TimeSlot slot, Map<CandidateCategory, Integer> remaining,
                                        Set<String> requiredIds) {
        List<Candidate> within = slot.candidates().stream()
                .filter(c -> remaining.get(c.category()) > 0 || requiredIds.contains(c.id()))
                .toList();
        return within.size() == slot.size() ? slot : new TimeSlot(slot.type(), within);
    }
}
