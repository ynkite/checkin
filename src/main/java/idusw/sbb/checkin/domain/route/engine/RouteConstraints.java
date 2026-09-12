package idusw.sbb.checkin.domain.route.engine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 시각을 하드코딩하지 않기 위한 여행 레벨 제약 조건 묶음. 실제 도착·배치 시각은
 * 이 제약을 만족하는 계산 결과로 나온다 (기획서 3장 3단계).
 *
 * <p>체크인/체크아웃 가능 시각은 여기 두지 않는다 — 그건 숙소 속성이라 {@link Anchor} 가
 * 유일한 출처다 (설계 결정 9/12). 이 타입은 숙소가 아니라 여행 전체에 걸리는 제약만 담는다.
 */
public record RouteConstraints(
        LocalDateTime firstDayArrival,
        LocalTime lastDayDepartureTime,
        LocalTime lunchWindowStart,
        LocalTime lunchWindowEnd,
        LocalTime dinnerWindowStart,
        LocalTime dinnerWindowEnd,
        Duration maxDailyTravelTime,
        GeoPoint arrivalPoint,
        GeoPoint departurePoint
) {

    public RouteConstraints {
        if (arrivalPoint == null) {
            throw new IllegalArgumentException("arrivalPoint must not be null");
        }
        requireOrderedWindow(lunchWindowStart, lunchWindowEnd, "lunch");
        requireOrderedWindow(dinnerWindowStart, dinnerWindowEnd, "dinner");
        if (maxDailyTravelTime != null && maxDailyTravelTime.isNegative()) {
            throw new IllegalArgumentException("maxDailyTravelTime must not be negative");
        }
        // 왕복이 대부분이라 귀가 출발 지점을 따로 안 주면 도착 지점과 같다고 본다.
        departurePoint = departurePoint != null ? departurePoint : arrivalPoint;
    }

    private static void requireOrderedWindow(LocalTime start, LocalTime end, String label) {
        if (start != null && end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException(label + " window start must be before end");
        }
    }

    public boolean isWithinLunchWindow(LocalTime time) {
        return isWithinWindow(time, lunchWindowStart, lunchWindowEnd);
    }

    public boolean isWithinDinnerWindow(LocalTime time) {
        return isWithinWindow(time, dinnerWindowStart, dinnerWindowEnd);
    }

    private static boolean isWithinWindow(LocalTime time, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return true;
        }
        return !time.isBefore(start) && !time.isAfter(end);
    }

    /**
     * 우회비용(km) = d(도착지점, p) + d(p, 귀가지점) − d(도착지점, 귀가지점).
     * 값이 작을수록 "귀가 방향"에 가깝다 (설계 결정 9/12 결정 2).
     */
    public double detourCostKm(GeoPoint candidate) {
        return arrivalPoint.distanceKmTo(candidate)
                + candidate.distanceKmTo(departurePoint)
                - arrivalPoint.distanceKmTo(departurePoint);
    }
}
