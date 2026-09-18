package idusw.sbb.checkin.domain.route.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.Collectors;

/**
 * 슬롯 하나 + 시작 지점 + 시작 시각 → 최적 조합·순서·종료 상태. 순수 함수다 — 같은
 * 입력이면 항상 같은 결과를 준다 ({@code ..\동선엔진_작업브리핑.md} 결정 6·8·9).
 *
 * <p>결정 8 에 따라 **부분집합 순열**을 전부 본다 — 5개 슬롯이면
 * P(5,1)+P(5,2)+P(5,3)+P(5,4)+P(5,5) = 325 가지. "순열이 0개" 라는 폴백은 없다 —
 * 방문 0곳도 정상적인 결과 중 하나로 처음부터 탐색 공간에 있다.
 *
 * <p>목적함수(결정 8): (1) 방문 수 최대 → (2) 이동비용 총합 최소 → (3) 후보 id 사전순.
 *
 * <p>시뮬레이션 규칙(결정 9-3): 슬롯의 첫 방문은 {@code max(windowStart, 도착시각)} 에서
 * 시작한다(창이 열리기 전에 도착하면 기다린다). 이후 방문은 그대로 이어 붙인다.
 * **창을 넘기는 기준은 도착(끝)이지 시작이 아니다** — 어떤 방문이든 도착 시각이
 * {@code windowEnd} 를 넘으면 그 조합 전체가 탈락한다.
 *
 * <p>휴무일(closedDays) 판정은 하지 않는다 — 실제 달력 날짜를 모른다(SlotBuilder와 같은
 * 한계). 후보 자신의 영업시간이 도착 시각을 포함하는지만 본다.
 *
 * <p>이동비용은 생성자로 주입되는 함수 하나로 계산한다. 지금은 Haversine 거리를
 * {@code averageSpeedKmh} 로 나눠 "분" 을 만들지만, 나중에 {@code TravelTimeProvider} 를
 * 끼우면 이 함수만 바뀐다 — 계산식은 안 바뀐다 (결정 2/5-5 와 같은 원칙).
 */
public final class SlotOptimizer {

    private static final Logger log = LoggerFactory.getLogger(SlotOptimizer.class);

    public static final double DEFAULT_AVERAGE_SPEED_KMH = TravelCostMetric.DEFAULT_AVERAGE_SPEED_KMH;

    private final ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes;

    public SlotOptimizer(ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes) {
        if (travelTimeMinutes == null) {
            throw new IllegalArgumentException("travelTimeMinutes must not be null");
        }
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public static SlotOptimizer withHaversineEstimate(double averageSpeedKmh) {
        return new SlotOptimizer(TravelCostMetric.haversineMinutes(averageSpeedKmh));
    }

    public static SlotOptimizer withDefaults() {
        return withHaversineEstimate(DEFAULT_AVERAGE_SPEED_KMH);
    }

    /**
     * @param slot        후보 최대 5개짜리 슬롯
     * @param startPoint  이 슬롯 진입 시점의 위치 (직전 슬롯의 종료 지점, 하루 첫 슬롯이면 앵커)
     * @param startTime   이 슬롯 진입 시점의 시각 (직전 슬롯의 종료 시각)
     * @param windowStart 이 슬롯의 시간 창 시작 — 첫 방문의 대기 기준
     * @param windowEnd   이 슬롯의 시간 창 끝 — 넘으면 그 방문(과 그 조합)은 탈락
     * @param maxVisits   이 슬롯에서 허용하는 최대 방문 수 (식사 슬롯=1, 결정 9-1)
     * @return 제약을 만족하는 조합 중 최선 — 하나도 없으면 방문 0곳
     */
    public SlotPlan optimize(TimeSlot slot, GeoPoint startPoint, LocalTime startTime,
                              LocalTime windowStart, LocalTime windowEnd, int maxVisits) {
        return optimize(slot, startPoint, startTime, windowStart, windowEnd, maxVisits, Set.of());
    }

    /**
     * @param requiredIds 반드시 포함해야 하는 후보 id (사용자가 직접 요청한 장소). 이 슬롯에 들어 있는
     *                    필수 후보를 <b>전부</b> 담지 못하는 조합은 버린다. 그런 조합이 하나도 없으면
     *                    제약을 풀고 WARN 을 남긴다 — 요청 장소 때문에 슬롯을 통째로 비우는 건
     *                    "왔다갔다 하지 않는 동선" 보다 나쁜 결과다.
     */
    public SlotPlan optimize(TimeSlot slot, GeoPoint startPoint, LocalTime startTime,
                              LocalTime windowStart, LocalTime windowEnd, int maxVisits,
                              Set<String> requiredIds) {
        if (slot == null) {
            throw new IllegalArgumentException("slot must not be null");
        }
        if (startPoint == null || startTime == null) {
            throw new IllegalArgumentException("startPoint/startTime must not be null");
        }
        if (windowStart == null || windowEnd == null) {
            throw new IllegalArgumentException("windowStart/windowEnd must not be null");
        }
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("windowStart must not be after windowEnd");
        }
        if (maxVisits < 0) {
            throw new IllegalArgumentException("maxVisits must not be negative");
        }

        Set<String> mustInclude = slot.candidates().stream()
                .map(Candidate::id)
                .filter(requiredIds::contains)
                .collect(Collectors.toSet());

        SlotPlan best = search(slot, startPoint, startTime, windowStart, windowEnd, maxVisits, mustInclude);
        if (!mustInclude.isEmpty() && best.visitCount() == 0) {
            SlotPlan withoutRequirement =
                    search(slot, startPoint, startTime, windowStart, windowEnd, maxVisits, Set.of());
            if (withoutRequirement.visitCount() > 0) {
                log.warn("필수 포함 후보를 넣을 수 있는 조합이 없어 제약을 푼다 — slot={} required={}",
                        slot.type(), mustInclude);
                return withoutRequirement;
            }
        }
        return best;
    }

    private SlotPlan search(TimeSlot slot, GeoPoint startPoint, LocalTime startTime,
                             LocalTime windowStart, LocalTime windowEnd, int maxVisits, Set<String> mustInclude) {
        SlotPlan best = SlotPlan.empty(slot.type(), startPoint, startTime);

        for (List<Candidate> sequence : enumerateSequences(slot.candidates(), maxVisits)) {
            if (!containsAll(sequence, mustInclude)) {
                continue;
            }
            Optional<SlotPlan> candidate = simulate(slot.type(), sequence, startPoint, startTime, windowStart, windowEnd);
            if (candidate.isPresent() && isBetter(candidate.get(), best)) {
                best = candidate.get();
            }
        }

        return best;
    }

    private static boolean containsAll(List<Candidate> sequence, Set<String> mustInclude) {
        if (mustInclude.isEmpty()) {
            return true;
        }
        Set<String> ids = sequence.stream().map(Candidate::id).collect(Collectors.toSet());
        return ids.containsAll(mustInclude);
    }

    /** 크기 1..limit 인 모든 순서 있는 부분집합(순열) — 결정 8 의 P(n,1)+…+P(n,limit). */
    private static List<List<Candidate>> enumerateSequences(List<Candidate> pool, int maxVisits) {
        List<List<Candidate>> sequences = new ArrayList<>();
        int limit = Math.min(maxVisits, pool.size());
        backtrack(pool, new ArrayList<>(), new boolean[pool.size()], limit, sequences);
        return sequences;
    }

    private static void backtrack(List<Candidate> pool, List<Candidate> current, boolean[] used,
                                   int limit, List<List<Candidate>> out) {
        if (!current.isEmpty()) {
            out.add(new ArrayList<>(current));
        }
        if (current.size() == limit) {
            return;
        }
        for (int i = 0; i < pool.size(); i++) {
            if (used[i]) {
                continue;
            }
            used[i] = true;
            current.add(pool.get(i));
            backtrack(pool, current, used, limit, out);
            current.remove(current.size() - 1);
            used[i] = false;
        }
    }

    private Optional<SlotPlan> simulate(SlotType type, List<Candidate> sequence, GeoPoint startPoint,
                                         LocalTime startTime, LocalTime windowStart, LocalTime windowEnd) {
        GeoPoint currentPoint = startPoint;
        LocalTime currentTime = startTime;
        double totalCost = 0.0;

        for (int i = 0; i < sequence.size(); i++) {
            Candidate candidate = sequence.get(i);
            double leg = travelTimeMinutes.applyAsDouble(currentPoint, candidate.location());
            LocalTime arrival = currentTime.plusMinutes(ceilMinutes(leg));
            if (i == 0 && arrival.isBefore(windowStart)) {
                arrival = windowStart; // 창이 열리기 전 도착하면 기다린다
            }
            if (arrival.isAfter(windowEnd) || !isOpenAt(candidate, arrival)) {
                return Optional.empty();
            }

            totalCost += leg;
            currentTime = arrival.plusMinutes(candidate.dwellMinutes());
            currentPoint = candidate.location();
        }

        return Optional.of(new SlotPlan(type, sequence, currentPoint, currentTime, totalCost));
    }

    private static long ceilMinutes(double minutes) {
        return (long) Math.ceil(minutes);
    }

    /** 영업시간만 본다 — closedDays(휴무일)는 실제 날짜를 몰라 판정하지 않는다. */
    private static boolean isOpenAt(Candidate candidate, LocalTime arrival) {
        if (candidate.openTime() == null) {
            return true;
        }
        return !arrival.isBefore(candidate.openTime()) && !arrival.isAfter(candidate.closeTime());
    }

    private static boolean isBetter(SlotPlan candidate, SlotPlan current) {
        if (candidate.visitCount() != current.visitCount()) {
            return candidate.visitCount() > current.visitCount();
        }
        if (candidate.travelCost() != current.travelCost()) {
            return candidate.travelCost() < current.travelCost();
        }
        return compareIds(candidate.visitOrder(), current.visitOrder()) < 0;
    }

    private static int compareIds(List<Candidate> a, List<Candidate> b) {
        for (int i = 0; i < a.size(); i++) {
            int cmp = a.get(i).id().compareTo(b.get(i).id());
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
