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
        GeoPoint departurePoint,
        LocalTime dayStartTime,
        LocalTime dayEndTime
) {

    private static final LocalTime DEFAULT_DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_DAY_END = LocalTime.of(21, 0);

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
        dayStartTime = dayStartTime != null ? dayStartTime : DEFAULT_DAY_START;
        dayEndTime = dayEndTime != null ? dayEndTime : DEFAULT_DAY_END;
        if (!dayStartTime.isBefore(dayEndTime)) {
            throw new IllegalArgumentException("dayStartTime must be before dayEndTime");
        }
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

    /** 첫날은 {@code max(firstDayArrival, dayStartTime)} — 새벽 도착이라고 dayStartTime 전부터 채우지 않는다 (결정 7-1). */
    public LocalTime effectiveDayStart(boolean isFirstDay) {
        if (!isFirstDay || firstDayArrival == null) {
            return dayStartTime;
        }
        LocalTime arrivalTime = firstDayArrival.toLocalTime();
        return arrivalTime.isAfter(dayStartTime) ? arrivalTime : dayStartTime;
    }

    /** 마지막 날은 {@code min(dayEndTime, lastDayDepartureTime)} — 더 이른 쪽에서 하루가 끝난다 (결정 7-1). */
    public LocalTime effectiveDayEnd(boolean isLastDay) {
        if (!isLastDay || lastDayDepartureTime == null) {
            return dayEndTime;
        }
        return lastDayDepartureTime.isBefore(dayEndTime) ? lastDayDepartureTime : dayEndTime;
    }

    /**
     * 슬롯 시간 창의 시작 (결정 7-2 표). {@code SlotBuilder} 와 {@code DayPlanner} 가 같은
     * 창 계산을 각자 중복 구현하지 않도록 여기 하나로 둔다. 점심/저녁 창이 설정돼 있어야 한다.
     */
    public LocalTime windowStart(SlotType type, boolean isFirstDay) {
        return switch (type) {
            case MORNING_ACTIVITY -> effectiveDayStart(isFirstDay);
            case LUNCH -> lunchWindowStart;
            case AFTERNOON_ACTIVITY -> lunchWindowEnd;
            case DINNER -> dinnerWindowStart;
            case EVENING_ACTIVITY -> dinnerWindowEnd;
        };
    }

    /** 슬롯 시간 창의 끝 (결정 7-2 표). */
    public LocalTime windowEnd(SlotType type, boolean isLastDay) {
        return switch (type) {
            case MORNING_ACTIVITY -> lunchWindowStart;
            case LUNCH -> lunchWindowEnd;
            case AFTERNOON_ACTIVITY -> dinnerWindowStart;
            case DINNER -> dinnerWindowEnd;
            case EVENING_ACTIVITY -> effectiveDayEnd(isLastDay);
        };
    }
}
