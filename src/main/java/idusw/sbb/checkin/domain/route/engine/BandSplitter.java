package idusw.sbb.checkin.domain.route.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

/**
 * 앵커 기준 밴드 분할. 후보를 NEAR/MID/RETURN 으로 나누고, 여행 일수(N)에 맞게
 * 일자별 후보 풀로 접는다 ({@code ..\동선엔진_작업브리핑.md} 설계 결정 9/12, 결정 1·2·3·5).
 *
 * <p>거리·비용 계산은 전부 생성자로 주입되는 {@code costMetric} 을 통해서만 한다.
 * 지금은 {@link Haversine#distanceKm} 로 km 단위를 쓰지만, 나중에
 * {@code TravelTimeProvider} 를 끼우면 분 단위로 바뀐다 — 그때도 이 클래스의 계산식은
 * 하나도 안 바뀐다 (결정 5-(5)). 그래서 {@code nearBoundary}/{@code midBoundary}/
 * {@code maxDetourCostOverride} 도 전부 costMetric 과 같은 단위로 취급한다.
 *
 * <p>방위각(bearing)은 예외다 — 이건 위경도의 기하학적 성질이라 비용 단위와 무관하게
 * 항상 실제 좌표로 계산한다.
 *
 * <h2>분배는 예약 → 보충 → 잔여 3단계다</h2>
 * <ol>
 *   <li><b>예약</b> — 마지막 날(우회비용 순) · 첫날(NEAR) · 중간 날(MID 방위각 덩이)이 각각
 *       자기 밴드에서 {@code minPerDay} 만큼만 먼저 떼어 둔다. 끼니 바닥(FOOD 2곳)이
 *       활동 후보보다 먼저다 — 식당이 하나뿐인 날은 저녁이 반드시 빈다.</li>
 *   <li><b>보충</b> — 못 채운 밴드가 인접 밴드에서 가까운 순으로 빌린다(결정 3).
 *       빌리는 순서는 첫날 → 중간 날로 기존 순서를 그대로 둔다.</li>
 *   <li><b>잔여</b> — 남은 후보는 <b>상한 없이</b> 원래 밴드의 날로 돌아간다.</li>
 * </ol>
 *
 * <p>첫날에 고정 상한은 두지 않는다. 예전 구조는 첫날이 NEAR 를 통째로 삼켜 소진했고,
 * 그래서 중간 날의 보충이 빌릴 후보를 못 찾아 항상 0개로 비었다 — 후보가 숙소 반경에
 * 몰리는 실제 카카오 데이터에서는 MID 밴드 자체가 비기 때문에 더 확실히 그랬다.
 * 예약이 첫날의 몫을 {@code minPerDay} 로 묶어 두면 그 문제는 사라지고, 3단계가 나머지를
 * 다시 첫날에 돌려주므로 후보가 버려지지도 않는다.
 */
public final class BandSplitter {

    public static final int DEFAULT_MIN_PER_DAY = 8;
    public static final double DEFAULT_NEAR_BOUNDARY = 5.0;
    public static final double DEFAULT_MID_BOUNDARY = 25.0;

    /**
     * 귀가 밴드 절대거리 가드 (결정 11-(2)). 우회비용은 귀가 방향 장소를 앵커에서 멀어도 정상
     * 후보로 보지만, {@code AiRouteService.postProcessRoute} 는 중심점 40km(도로거리) 로 자른다.
     * 기준이 달라 엔진이 낸 장소가 뒤에서 조용히 지워지므로, 애초에 넣지 않는다.
     *
     * <p>이 값만은 {@code costMetric} 이 아니라 <b>항상 실제 km</b> 로 잰다 — 뒷단계 규칙이 km 라서다.
     * 나중에 {@code TravelTimeProvider} 로 비용 단위가 분으로 바뀌어도 이 가드는 km 그대로다.
     */
    public static final double MAX_RETURN_DISTANCE_KM = 36.0;

    /** 하루의 식사 슬롯 수 (점심·저녁). 끼니 바닥이 이 값이다 — 결정 9-(1) 의 식사 슬롯 상한 1 × 2슬롯. */
    private static final int MEAL_SLOTS_PER_DAY = 2;

    private static final double DEFAULT_DETOUR_RATIO = 0.3;
    private static final double DEFAULT_DETOUR_MIN = 5.0;
    private static final double DEFAULT_DETOUR_MAX = 20.0;

    private final double nearBoundary;
    private final double midBoundary;
    private final int minPerDay;
    private final Double maxDetourCostOverride;
    private final ToDoubleBiFunction<GeoPoint, GeoPoint> costMetric;

    public BandSplitter(double nearBoundary, double midBoundary, int minPerDay,
                         Double maxDetourCostOverride, ToDoubleBiFunction<GeoPoint, GeoPoint> costMetric) {
        if (nearBoundary <= 0) {
            throw new IllegalArgumentException("nearBoundary must be positive");
        }
        if (midBoundary <= nearBoundary) {
            throw new IllegalArgumentException("midBoundary must be greater than nearBoundary");
        }
        if (minPerDay <= 0) {
            throw new IllegalArgumentException("minPerDay must be positive");
        }
        if (maxDetourCostOverride != null && maxDetourCostOverride <= 0) {
            throw new IllegalArgumentException("maxDetourCostOverride must be positive");
        }
        if (costMetric == null) {
            throw new IllegalArgumentException("costMetric must not be null");
        }
        this.nearBoundary = nearBoundary;
        this.midBoundary = midBoundary;
        this.minPerDay = minPerDay;
        this.maxDetourCostOverride = maxDetourCostOverride;
        this.costMetric = costMetric;
    }

    /**
     * 5km/25km 경계, minPerDay=8(식사 2 + 활동 3~4 + 여유, 결정 7-3), 비율 기반 우회비용 임계,
     * Haversine 비용 — 기본값 그대로.
     */
    public static BandSplitter withDefaults() {
        return new BandSplitter(DEFAULT_NEAR_BOUNDARY, DEFAULT_MID_BOUNDARY,
                DEFAULT_MIN_PER_DAY, null, Haversine::distanceKm);
    }

    /**
     * @param anchor      숙소. 당일치기(totalDays=1)에는 없을 수 있다 — 그때는
     *                    {@code constraints.arrivalPoint()} 를 앵커 대신 쓴다.
     * @param constraints {@code arrivalPoint}/{@code departurePoint} 를 제공하는 여행 레벨 제약
     * @param candidates  분류 대상 후보 전체
     * @param totalDays   여행 일수 (N ≥ 1)
     * @return 일자 수만큼의 {@link DailyCandidatePool}, dayIndex 오름차순
     */
    public List<DailyCandidatePool> split(Anchor anchor, RouteConstraints constraints,
                                           List<Candidate> candidates, int totalDays) {
        return split(anchor, constraints, candidates, totalDays, Set.of());
    }

    /**
     * @param requiredIds 반드시 포함해야 하는 후보 id (사용자가 직접 요청한 장소). 36km 가드와 마지막 날
     *                    컷에서 면제한다 — 뒤 단계 {@code postProcessRoute} 도 사용자 요청 장소는
     *                    거리로 지우지 않으니 기준이 같다.
     */
    public List<DailyCandidatePool> split(Anchor anchor, RouteConstraints constraints,
                                           List<Candidate> candidates, int totalDays, Set<String> requiredIds) {
        if (constraints == null) {
            throw new IllegalArgumentException("constraints must not be null");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        if (totalDays < 1) {
            throw new IllegalArgumentException("totalDays must be at least 1");
        }

        GeoPoint anchorPoint = anchor != null ? anchor.location() : constraints.arrivalPoint();
        List<Candidate> withinGuard = candidates.stream()
                .filter(c -> requiredIds.contains(c.id())
                        || anchorPoint.distanceKmTo(c.location()) <= MAX_RETURN_DISTANCE_KM)
                .toList();
        Set<String> used = new HashSet<>();

        // ── 1단계 예약 ───────────────────────────────────────────────────
        // 날마다 자기 밴드에서 minPerDay 만큼만 먼저 떼어 둔다. 예전에는 첫날이 NEAR 를 통째로
        // 삼키고 markUsed 해서, 중간 날의 보충이 빌릴 후보를 하나도 못 찾았다 — 후보가 숙소
        // 근처에 몰리는 실제 카카오 데이터에서 중간 날이 항상 0개로 빈 원인이었다.
        //
        // 마지막 날이 가장 먼저다 (결정 12). 귀가 방향은 거리 구간이 아니라 우회비용 술어라,
        // 밴드를 먼저 자르면 숙소 근처의 "가는 길" 후보가 근거리 밴드에 묶여 영영 안 나온다.
        DailyCandidatePool lastDay = totalDays > 1
                ? buildLastDay(withinGuard, totalDays - 1, constraints, used, anchorPoint, requiredIds)
                : null;

        Map<DistanceBand, List<Candidate>> bands = classify(withoutUsed(withinGuard, used), anchorPoint);

        List<Candidate> nearBand = sortedByCost(bands.get(DistanceBand.NEAR), anchorPoint);
        boolean day0Relaxed = nearBand.size() < minPerDay;

        int middleDayCount = lastDay != null ? Math.max(totalDays - 2, 0) : 0;
        List<List<Candidate>> midClusters = middleDayCount > 0
                ? clusterByBearing(bands.get(DistanceBand.MID), middleDayCount, anchorPoint)
                : List.of();
        List<List<Candidate>> midBands = new ArrayList<>();
        boolean[] middleRelaxed = new boolean[middleDayCount];
        for (int i = 0; i < middleDayCount; i++) {
            List<Candidate> cluster = sortedByCost(midClusters.get(i), anchorPoint);
            middleRelaxed[i] = cluster.size() < minPerDay;
            midBands.add(cluster);
        }

        // 1단계-(a) 끼니 바닥이 활동 후보보다 먼저다. 하루에 식사 슬롯이 둘(점심·저녁)인데
        // 그 날 풀에 FOOD 가 1곳뿐이면 점심이 그걸 쓰고 저녁은 반드시 빈다 — 자리가 없어서가
        // 아니라 먹을 곳이 없어서다. 거리순으로만 minPerDay 를 채우면 밴드에 식당이 적은 날이
        // 조용히 그 상태가 된다. 그래서 모든 날의 FOOD 2곳을 먼저 깔고 나머지를 채운다.
        List<Candidate> day0 = reserveMeals(nearBand,
                donors(bands, used, DistanceBand.MID, DistanceBand.RETURN), used, anchorPoint);
        List<List<Candidate>> middleDays = new ArrayList<>();
        for (int i = 0; i < middleDayCount; i++) {
            middleDays.add(reserveMeals(midBands.get(i),
                    donors(bands, used, DistanceBand.NEAR, DistanceBand.RETURN), used, anchorPoint));
        }

        // 1단계-(b) 남은 자리를 자기 밴드에서 minPerDay 까지
        reserve(day0, nearBand, used);
        for (int i = 0; i < middleDayCount; i++) {
            reserve(middleDays.get(i), midBands.get(i), used);
        }

        // ── 2단계 보충 ───────────────────────────────────────────────────
        // 못 채운 밴드가 인접 밴드에서 가까운 순으로 빌린다 (결정 3 규칙 그대로).
        //
        // 빌리는 순서는 첫날 → 중간 날로, 기존 순서를 그대로 둔다. 총 공급이
        // minPerDay × 일수에 못 미치면 누군가는 모자란 채로 끝나는데, 그때 첫날을 뒤로 미루면
        // 첫날 밴드에 식당이 하나뿐인 경우 점심이 그것을 쓰고 저녁이 빈다 — 스텁 강릉 19개가
        // 정확히 그 모양이다. 중간 날이 굶던 원인은 보충 순서가 아니라 첫날이 NEAR 를 통째로
        // 소진한 것이었고, 그건 1단계 예약이 이미 막았다. 순서를 바꿔 얻을 게 없다.
        topUp(day0, minPerDay, donors(bands, used, DistanceBand.MID, DistanceBand.RETURN), used, anchorPoint);
        for (int i = 0; i < middleDayCount; i++) {
            topUp(middleDays.get(i), minPerDay,
                    donors(bands, used, DistanceBand.NEAR, DistanceBand.RETURN), used, anchorPoint);
        }

        // ── 3단계 잔여 ───────────────────────────────────────────────────
        // 남은 후보는 상한 없이 원래 밴드의 날로 돌려준다. 첫날에 고정 상한을 두지 않는 이유는,
        // 후보가 많은 도시에서 첫날이 인위적으로 얇아지고 그만큼이 통째로 버려지기 때문이다.
        // 밴드가 자기 날을 갖는 한 여기서 버려지는 후보는 없다.
        appendRemainder(day0, nearBand, used);
        for (int i = 0; i < middleDayCount; i++) {
            appendRemainder(middleDays.get(i), midClusters.get(i), used);
        }

        List<DailyCandidatePool> result = new ArrayList<>();
        result.add(new DailyCandidatePool(0, DistanceBand.NEAR, day0, day0Relaxed));
        if (lastDay == null) {
            return List.copyOf(result);
        }
        for (int i = 0; i < middleDayCount; i++) {
            result.add(new DailyCandidatePool(1 + i, DistanceBand.MID, middleDays.get(i), middleRelaxed[i]));
        }

        List<Candidate> lastDayPool = new ArrayList<>(lastDay.candidates());
        appendRemainder(lastDayPool, sortedByCost(bands.get(DistanceBand.RETURN), anchorPoint), used);
        result.add(new DailyCandidatePool(
                lastDay.dayIndex(), DistanceBand.RETURN, lastDayPool, lastDay.relaxed()));

        return List.copyOf(result);
    }

    // ── 밴드 분류 (결정 1) ──────────────────────────────────────────────

    private Map<DistanceBand, List<Candidate>> classify(List<Candidate> candidates, GeoPoint anchorPoint) {
        Map<DistanceBand, List<Candidate>> bands = new EnumMap<>(DistanceBand.class);
        for (DistanceBand band : DistanceBand.values()) {
            bands.put(band, new ArrayList<>());
        }
        for (Candidate candidate : candidates) {
            double cost = costMetric.applyAsDouble(anchorPoint, candidate.location());
            DistanceBand band = cost < nearBoundary ? DistanceBand.NEAR
                    : cost < midBoundary ? DistanceBand.MID
                    : DistanceBand.RETURN;
            bands.get(band).add(candidate);
        }
        return bands;
    }

    // ── 3단계의 부품 ────────────────────────────────────────────────────

    /** 앵커에서 가까운 순. 동점은 id 사전순으로 끊는다 — 같은 입력에 같은 결과가 나와야 한다. */
    private List<Candidate> sortedByCost(List<Candidate> pool, GeoPoint anchorPoint) {
        return pool.stream()
                .sorted(Comparator
                        .comparingDouble((Candidate c) -> costMetric.applyAsDouble(anchorPoint, c.location()))
                        .thenComparing(Candidate::id))
                .toList();
    }

    /**
     * 1단계-(a) : 끼니 바닥. 그 날 풀에 FOOD 를 {@link #MEAL_SLOTS_PER_DAY} 곳까지 먼저 넣는다.
     * 자기 밴드에 식당이 모자라면 인접 밴드에서 가까운 순으로 당겨 온다 — 활동 후보를 거리순으로
     * 채우기 <b>전에</b> 해야 한다. 나중에 채우면 그 자리가 이미 없다.
     */
    private List<Candidate> reserveMeals(List<Candidate> bandPool, List<Candidate> donorPool,
                                          Set<String> used, GeoPoint anchorPoint) {
        List<Candidate> meals = new ArrayList<>();
        takeFood(meals, bandPool, used);
        if (meals.size() < MEAL_SLOTS_PER_DAY) {
            takeFood(meals, sortedByCost(donorPool, anchorPoint), used);
        }
        return meals;
    }

    private static void takeFood(List<Candidate> target, List<Candidate> pool, Set<String> used) {
        for (Candidate candidate : pool) {
            if (target.size() >= MEAL_SLOTS_PER_DAY) {
                break;
            }
            if (used.contains(candidate.id()) || candidate.category() != CandidateCategory.FOOD) {
                continue;
            }
            target.add(candidate);
            used.add(candidate.id());
        }
    }

    /** 1단계-(b) : 자기 밴드에서 minPerDay 까지 채운다. 모자라면 모자란 대로 두고 2단계가 채운다. */
    private void reserve(List<Candidate> reserved, List<Candidate> bandPool, Set<String> used) {
        for (Candidate candidate : bandPool) {
            if (reserved.size() >= minPerDay) {
                break;
            }
            if (used.contains(candidate.id())) {
                continue;
            }
            reserved.add(candidate);
            used.add(candidate.id());
        }
    }

    /** 3단계 : 예약·보충에서 안 쓰인 나머지를 상한 없이 이 날에 되돌린다. */
    private static void appendRemainder(List<Candidate> target, List<Candidate> bandPool, Set<String> used) {
        for (Candidate candidate : bandPool) {
            if (used.contains(candidate.id())) {
                continue;
            }
            target.add(candidate);
            used.add(candidate.id());
        }
    }

    /** 2단계의 빌릴 곳 — 인접 밴드에서 아직 안 쓰인 것들. */
    private static List<Candidate> donors(Map<DistanceBand, List<Candidate>> bands, Set<String> used,
                                           DistanceBand... from) {
        List<Candidate> donors = new ArrayList<>();
        for (DistanceBand band : from) {
            donors.addAll(withoutUsed(bands.get(band), used));
        }
        return donors;
    }

    // ── 중간 날 방위각 분할 (결정 5-(2)) ─────────────────────────────────

    /** 방위각 정렬 후, 이웃 간 간격이 가장 큰 지점을 잘라 clusterCount 덩이로 나눈다 (결정 5-(2)). */
    private List<List<Candidate>> clusterByBearing(List<Candidate> pool, int clusterCount, GeoPoint anchorPoint) {
        List<Candidate> sorted = pool.stream()
                .sorted(Comparator.comparingDouble(c -> bearingDegrees(anchorPoint, c.location())))
                .toList();

        List<List<Candidate>> clusters = new ArrayList<>();
        if (sorted.size() <= 1 || clusterCount <= 1) {
            clusters.add(new ArrayList<>(sorted));
            for (int i = 1; i < clusterCount; i++) {
                clusters.add(new ArrayList<>());
            }
            return clusters;
        }

        record Gap(int index, double size) {
        }

        List<Gap> gaps = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            double size = bearingDegrees(anchorPoint, sorted.get(i + 1).location())
                    - bearingDegrees(anchorPoint, sorted.get(i).location());
            gaps.add(new Gap(i, size));
        }

        int cutCount = Math.min(clusterCount - 1, gaps.size());
        List<Integer> cutIndexes = gaps.stream()
                .sorted(Comparator.comparingDouble(Gap::size).reversed())
                .limit(cutCount)
                .map(Gap::index)
                .sorted()
                .toList();

        int start = 0;
        for (int cut : cutIndexes) {
            clusters.add(new ArrayList<>(sorted.subList(start, cut + 1)));
            start = cut + 1;
        }
        clusters.add(new ArrayList<>(sorted.subList(start, sorted.size())));

        while (clusters.size() < clusterCount) {
            clusters.add(new ArrayList<>());
        }
        return clusters;
    }

    private static double bearingDegrees(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLng = Math.toRadians(to.longitude() - from.longitude());

        double y = Math.sin(deltaLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    // ── 마지막 날 (결정 2 + 결정 5-(4)) ──────────────────────────────────

    /**
     * 마지막 날 풀 = <b>전체 후보 중</b> 우회비용이 임계 이하인 것들 (결정 12). 거리 하한은 없다 —
     * 카카오가 숙소 반경 20km 만 주므로 "25km 이상" 같은 하한을 두면 귀가 풀이 항상 빈다.
     *
     * <p>임계 이내가 {@code minPerDay} 를 넘으면 <b>귀가거점에 가까운 순</b>으로 자른다. 귀가거점이
     * 수백 km 밖이면 숙소 옆 후보도 우회비용이 0에 가까워 임계를 통과하는데, 그것들까지 마지막 날이
     * 가져가면 첫날 근거리 풀이 빈다. 실제로 집 쪽으로 나아가는 후보를 고르는 기준이 필요하다.
     *
     * <p>임계 이내가 모자라면 완화 폴백(결정 5-(4))으로 우회비용 오름차순으로 채운다 — 그때는
     * 방향의 질 자체가 희소 자원이라 기준이 다르다.
     */
    private DailyCandidatePool buildLastDay(List<Candidate> candidates, int dayIndex, RouteConstraints constraints,
                                             Set<String> used, GeoPoint anchorPoint, Set<String> requiredIds) {
        double maxDetourCost = resolveMaxDetourCost(anchorPoint, constraints);

        List<Candidate> withinThreshold = candidates.stream()
                .filter(c -> detourCost(anchorPoint, constraints, c.location()) <= maxDetourCost)
                .sorted(Comparator
                        .comparingDouble((Candidate c) -> c.location().distanceKmTo(constraints.departurePoint()))
                        .thenComparing(Candidate::id))
                .toList();

        boolean relaxed = withinThreshold.size() < minPerDay;
        List<Candidate> ranked = relaxed
                ? candidates.stream()
                        .sorted(Comparator
                                .comparingDouble((Candidate c) -> detourCost(anchorPoint, constraints, c.location()))
                                .thenComparing(Candidate::id))
                        .toList()
                : withinThreshold;

        // 필수 후보는 컷에서 먼저 자리를 잡는다 — 여기서 밀리면 그 날 풀에 아예 안 들어간다.
        List<Candidate> prioritized = ranked.stream()
                .sorted(Comparator.comparingInt(c -> requiredIds.contains(c.id()) ? 0 : 1))
                .toList();
        List<Candidate> lastDayPool = prioritized.subList(0, Math.min(minPerDay, prioritized.size()));

        markUsed(used, lastDayPool);
        return new DailyCandidatePool(dayIndex, DistanceBand.RETURN, lastDayPool, relaxed);
    }

    /** 패키지 공개 — 테스트가 clamp 결과(계수가 아니라 상한이 값을 정하는지)를 직접 확인한다. */
    double resolveMaxDetourCost(GeoPoint anchorPoint, RouteConstraints constraints) {
        if (maxDetourCostOverride != null) {
            return maxDetourCostOverride;
        }
        double homeStretch = costMetric.applyAsDouble(anchorPoint, constraints.departurePoint());
        double ratioBased = DEFAULT_DETOUR_RATIO * homeStretch;
        return Math.max(DEFAULT_DETOUR_MIN, Math.min(DEFAULT_DETOUR_MAX, ratioBased));
    }

    /** 우회비용 = cost(앵커, p) + cost(p, 귀가지점) − cost(앵커, 귀가지점) (결정 2). */
    private double detourCost(GeoPoint anchorPoint, RouteConstraints constraints, GeoPoint candidate) {
        return costMetric.applyAsDouble(anchorPoint, candidate)
                + costMetric.applyAsDouble(candidate, constraints.departurePoint())
                - costMetric.applyAsDouble(anchorPoint, constraints.departurePoint());
    }

    // ── 공용 유틸 (결정 3의 "인접에서 가까운 순으로 끌어오기" 재사용) ────────

    /**
     * target 이 minCount 에 못 미치면 donorPool 에서 앵커와 가까운 순으로 채운다.
     *
     * <p>반환값은 "실제로 빌렸는가" 이고 <b>{@code relaxed} 의 근거가 아니다.</b> 빌릴 후보조차
     * 없으면 false 가 나오는데, 그건 완화가 필요 없었다는 뜻이 아니라 완화에 실패했다는 뜻이다.
     * 그 false 를 {@code relaxed} 로 쓰던 동안 MID 가 0개인 날이 완화 표시 없이 지나갔다.
     * {@code relaxed} 는 자기 밴드가 {@code minPerDay} 를 못 채웠는지로 따로 정한다.
     */
    private boolean topUp(List<Candidate> target, int minCount, List<Candidate> donorPool,
                           Set<String> used, GeoPoint anchorPoint) {
        if (target.size() >= minCount) {
            return false;
        }
        List<Candidate> sortedDonors = donorPool.stream()
                .filter(c -> !used.contains(c.id()))
                .sorted(Comparator.comparingDouble(c -> costMetric.applyAsDouble(anchorPoint, c.location())))
                .toList();

        boolean borrowed = false;
        for (Candidate candidate : sortedDonors) {
            if (target.size() >= minCount) {
                break;
            }
            target.add(candidate);
            used.add(candidate.id());
            borrowed = true;
        }
        return borrowed;
    }

    private static List<Candidate> withoutUsed(List<Candidate> pool, Set<String> used) {
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : pool) {
            if (!used.contains(candidate.id())) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static void markUsed(Set<String> used, List<Candidate> pool) {
        for (Candidate candidate : pool) {
            used.add(candidate.id());
        }
    }
}
