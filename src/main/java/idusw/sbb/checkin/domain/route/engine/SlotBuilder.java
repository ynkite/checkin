package idusw.sbb.checkin.domain.route.engine;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;

/**
 * 하루치 후보 풀({@link DailyCandidatePool})을 슬롯들({@link TimeSlot})로 자른다
 * ({@code ..\동선엔진_작업브리핑.md} 설계 결정 9/12, 결정 6·7).
 *
 * <p>결정 7-2 에 따라 **시계를 굴리지 않는다.** 슬롯의 시간 창은 방문 순서를 몰라도
 * {@link RouteConstraints} 의 점심/저녁 창만으로 정해진다 — 첫날의 시작만
 * {@link RouteConstraints#effectiveDayStart} 로 당겨지거나 늦춰진다.
 *
 * <pre>
 * MORNING   = [하루 시작, 점심창 시작]
 * LUNCH     = 점심 창
 * AFTERNOON = [점심창 끝, 저녁창 시작]
 * DINNER    = 저녁 창
 * EVENING   = [저녁창 끝, 하루 종료]
 * </pre>
 *
 * <p>창이 0 이하로 접히면 그 슬롯 자체를 만들지 않는다. 창은 있는데 조건에 맞는 후보가
 * 없으면 빈 {@link TimeSlot}(size 0)을 만든다 — "시간이 없었다"와 "후보가 없었다"는
 * 다른 신호라 구분한다 (결정 7 "그 외 동의한 것").
 *
 * <p>각 슬롯은 결정 4의 3단계로 최대 5개까지 자른다 — (0) 식사 슬롯은 FOOD, 활동 슬롯은
 * TOUR/CAFE 로 카테고리부터 좁히고 (1) {@code filter} 통과(반려동물/무장애/키즈존 —
 * 지금은 항상 통과, 작업 5에서 실제 조건으로 교체) → (2) 슬롯 시간 창과 영업시간이
 * 겹치는지 → (3) 기준점에서 가까운 순 상위 5. 같은 날 이미 다른 슬롯에 배정된 후보는
 * 그 자리에서 소비되어 제외된다({@code usedToday}).
 *
 * <p>기준점은 그 날의 첫 슬롯은 앵커(없으면 도착 지점), 이후 슬롯은 **직전 슬롯 후보의
 * 무게중심(centroid)** 이다 — 실제 방문 순서(3b 의 결과물) 없이도 계산되고 순환 의존이
 * 없다 (결정 7-2). 직전 슬롯이 비었으면 기준점은 그대로 유지한다.
 *
 * <p>휴무일(closedDays) 판정은 하지 않는다 — 이 시점에는 dayIndex 뿐, 이 날이 실제
 * 달력 며칠인지(여행 시작일)를 모른다. 영업시간(시각)이 슬롯 창과 겹치는지만 본다.
 *
 * <p>{@code maxDailyTravelTime} 검증도 여기서 하지 않는다 — 총 이동시간은 방문 순서가
 * 정해져야 나오므로 3b(SlotOptimizer) 의 몫이다 (결정 7-2).
 */
public final class SlotBuilder {

    private final Predicate<Candidate> filter;
    private final ToDoubleBiFunction<GeoPoint, GeoPoint> costMetric;

    public SlotBuilder(Predicate<Candidate> filter, ToDoubleBiFunction<GeoPoint, GeoPoint> costMetric) {
        if (filter == null) {
            throw new IllegalArgumentException("filter must not be null");
        }
        if (costMetric == null) {
            throw new IllegalArgumentException("costMetric must not be null");
        }
        this.filter = filter;
        this.costMetric = costMetric;
    }

    /** 필터 없이(항상 통과) Haversine 비용으로 자른다 — 작업 5 전까지의 기본값. */
    public static SlotBuilder withDefaults() {
        return new SlotBuilder(candidate -> true, Haversine::distanceKm);
    }

    /**
     * @param pool        하루치 후보 풀 (BandSplitter 산출물)
     * @param anchor      숙소. 당일치기엔 없을 수 있다 — 그때는 {@code constraints.arrivalPoint()} 를 쓴다.
     * @param constraints 점심/저녁 창, dayStartTime/dayEndTime 등 여행 레벨 제약
     * @param totalDays   여행 일수 (첫날/마지막날 특례 판단에 필요)
     * @return 그 날 시간이 있는 슬롯만, 하루 리듬 순서대로
     */
    public List<TimeSlot> build(DailyCandidatePool pool, Anchor anchor, RouteConstraints constraints, int totalDays) {
        return build(pool, anchor, constraints, totalDays, Set.of());
    }

    /**
     * @param requiredIds 반드시 포함해야 하는 후보 id (사용자가 직접 요청한 장소). 상위 5 컷에서 먼저
     *                    집어넣는다 — 여기서 잘리면 {@code SlotOptimizer} 는 그 후보를 볼 기회조차 없다.
     */
    public List<TimeSlot> build(DailyCandidatePool pool, Anchor anchor, RouteConstraints constraints,
                                 int totalDays, Set<String> requiredIds) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null");
        }
        if (constraints == null) {
            throw new IllegalArgumentException("constraints must not be null");
        }
        if (totalDays < 1) {
            throw new IllegalArgumentException("totalDays must be at least 1");
        }
        if (pool.dayIndex() < 0 || pool.dayIndex() >= totalDays) {
            throw new IllegalArgumentException("dayIndex must be within [0, totalDays)");
        }
        if (constraints.lunchWindowStart() == null || constraints.lunchWindowEnd() == null
                || constraints.dinnerWindowStart() == null || constraints.dinnerWindowEnd() == null) {
            throw new IllegalArgumentException("lunch/dinner window must be set for SlotBuilder");
        }

        boolean isFirstDay = pool.dayIndex() == 0;
        boolean isLastDay = pool.dayIndex() == totalDays - 1;

        GeoPoint referencePoint = anchor != null ? anchor.location() : constraints.arrivalPoint();
        Set<String> usedToday = new HashSet<>();
        List<TimeSlot> result = new ArrayList<>();

        for (SlotType type : SlotType.values()) {
            LocalTime windowStart = constraints.windowStart(type, isFirstDay);
            LocalTime windowEnd = constraints.windowEnd(type, isLastDay);
            if (!windowStart.isBefore(windowEnd)) {
                continue; // 창이 0 이하로 접힘 — 이 슬롯은 만들지 않는다
            }

            List<Candidate> cut = cut(pool.candidates(), type, windowStart, windowEnd, usedToday, referencePoint,
                    requiredIds);
            for (Candidate candidate : cut) {
                usedToday.add(candidate.id());
            }
            result.add(new TimeSlot(type, cut));

            if (!cut.isEmpty()) {
                referencePoint = centroid(cut);
            }
        }

        return List.copyOf(result);
    }

    /** 결정 4 의 3단계: 카테고리 적합 → 필터 통과 → 영업시간 겹침 → 기준점에서 가까운 순 상위 5. */
    private List<Candidate> cut(List<Candidate> dayPool, SlotType type, LocalTime windowStart, LocalTime windowEnd,
                                 Set<String> usedToday, GeoPoint referencePoint, Set<String> requiredIds) {
        boolean isMealSlot = type == SlotType.LUNCH || type == SlotType.DINNER;

        return dayPool.stream()
                .filter(c -> !usedToday.contains(c.id()))
                .filter(c -> isMealSlot == (c.category() == CandidateCategory.FOOD))
                .filter(filter)
                .filter(c -> overlapsWindow(c, windowStart, windowEnd))
                .sorted(Comparator
                        .comparingInt((Candidate c) -> requiredIds.contains(c.id()) ? 0 : 1)
                        .thenComparingDouble(c -> costMetric.applyAsDouble(referencePoint, c.location())))
                .limit(TimeSlot.MAX_CANDIDATES)
                .toList();
    }

    private static boolean overlapsWindow(Candidate candidate, LocalTime windowStart, LocalTime windowEnd) {
        if (candidate.openTime() == null) {
            return true; // 상시 영업
        }
        return candidate.openTime().isBefore(windowEnd) && windowStart.isBefore(candidate.closeTime());
    }

    private static GeoPoint centroid(List<Candidate> candidates) {
        double lat = candidates.stream().mapToDouble(c -> c.location().latitude()).average().orElseThrow();
        double lng = candidates.stream().mapToDouble(c -> c.location().longitude()).average().orElseThrow();
        return new GeoPoint(lat, lng);
    }
}
