package idusw.sbb.checkin.domain.route.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 겹치는지 → (3) 기준점에서 가까운 순 상위 5.
 *
 * <p><b>담기만 하고 소비하지 않는다 (결정 14).</b> 슬롯 풀끼리 같은 후보가 겹쳐도 되고, 실제로
 * 방문한 것만 빼는 일은 {@link DayPlanner} 가 한다. 예전에는 여기서 바로 소비했는데(결정 7-3),
 * 그러면 점심이 그 날 식당을 상위 5까지 전부 물고 가서 저녁이 굶는다 — 담긴 것(최대 5)과 실제로
 * 가는 것(식사 슬롯 1곳)이 결정 8 이후로 갈렸기 때문이다.
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

    private static final Logger log = LoggerFactory.getLogger(SlotBuilder.class);

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
        List<TimeSlot> result = new ArrayList<>();

        for (SlotType type : SlotType.values()) {
            LocalTime windowStart = constraints.windowStart(type, isFirstDay);
            LocalTime windowEnd = constraints.windowEnd(type, isLastDay);
            if (!windowStart.isBefore(windowEnd)) {
                // 창이 0 이하로 접힘 — 이 슬롯은 만들지 않는다.
                // "시간이 없었다" 와 "후보가 없었다" 는 다른 신호라 굴러가지 않게 남긴다.
                log.info("[route.engine] day{} {} · 시간창이 접혀 슬롯 없음 ({}~{})",
                        pool.dayIndex(), type, windowStart, windowEnd);
                continue;
            }

            List<Candidate> cut = cut(pool, type, windowStart, windowEnd, referencePoint, requiredIds);
            result.add(new TimeSlot(type, cut));

            if (!cut.isEmpty()) {
                referencePoint = centroid(cut);
            }
        }

        return List.copyOf(result);
    }

    /**
     * 결정 4 의 3단계: 카테고리 적합 → 필터 통과 → 영업시간 겹침 → 기준점에서 가까운 순 상위 5.
     *
     * <p><b>여기서 후보를 소비하지 않는다 (결정 14).</b> 슬롯 풀끼리 겹치는 건 정상이고, 실제로
     * 방문한 것만 빼는 건 {@link DayPlanner} 가 한다. 담는 쪽이 소비하면 점심이 그 날 식당을
     * 상위 5까지 전부 물고 가서 저녁이 굶는다 — 담긴 것과 간 것이 다르기 때문이다(결정 8).
     */
    private List<Candidate> cut(DailyCandidatePool pool, SlotType type, LocalTime windowStart, LocalTime windowEnd,
                                 GeoPoint referencePoint, Set<String> requiredIds) {
        boolean isMealSlot = type == SlotType.LUNCH || type == SlotType.DINNER;
        List<Candidate> dayPool = pool.candidates();

        // 단계를 나눠 담는다 — 어느 단계에서 몇 개가 떨어졌는지를 로그로 내야 한다.
        // 합쳐 놓으면 "슬롯이 비었다" 만 보이고 카테고리 탓인지 영업시간 탓인지 알 수 없다.
        List<Candidate> byCategory = dayPool.stream()
                .filter(c -> isMealSlot == (c.category() == CandidateCategory.FOOD))
                .toList();
        List<Candidate> byFilter = byCategory.stream().filter(filter).toList();
        List<Candidate> byHours = byFilter.stream()
                .filter(c -> overlapsWindow(c, windowStart, windowEnd))
                .toList();
        List<Candidate> cut = byHours.stream()
                .sorted(Comparator
                        .comparingInt((Candidate c) -> requiredIds.contains(c.id()) ? 0 : 1)
                        .thenComparingDouble(c -> costMetric.applyAsDouble(referencePoint, c.location())))
                .limit(TimeSlot.MAX_CANDIDATES)
                .toList();

        log.info("[route.engine] day{} {} ({}~{}) · 진입 {}개(FOOD {}·CAFE {}·TOUR {})"
                        + " · 탈락 카테고리 {}·필터 {}·영업시간 {}·상위{}컷 {} → 담김 {}개",
                pool.dayIndex(), type, windowStart, windowEnd,
                dayPool.size(), count(dayPool, CandidateCategory.FOOD), count(dayPool, CandidateCategory.CAFE),
                count(dayPool, CandidateCategory.TOUR),
                dayPool.size() - byCategory.size(), byCategory.size() - byFilter.size(),
                byFilter.size() - byHours.size(), TimeSlot.MAX_CANDIDATES, byHours.size() - cut.size(),
                cut.size());

        return cut;
    }

    private static long count(List<Candidate> pool, CandidateCategory category) {
        return pool.stream().filter(c -> c.category() == category).count();
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
