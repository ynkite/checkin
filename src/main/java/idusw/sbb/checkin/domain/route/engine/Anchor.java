package idusw.sbb.checkin.domain.route.engine;

import java.time.LocalTime;

/**
 * 여행의 앵커가 되는 숙소. 밴드 분할(A/B/C)의 기준점이다.
 *
 * <p>체크인/체크아웃 가능 시각의 유일한 출처다 (설계 결정 9/12) — {@link RouteConstraints}
 * 는 같은 값을 따로 갖지 않는다.
 */
public record Anchor(
        String id,
        String name,
        GeoPoint location,
        LocalTime checkInTime,
        LocalTime checkOutTime
) {

    public Anchor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
    }

    public double distanceKmTo(GeoPoint point) {
        return location.distanceKmTo(point);
    }

    public boolean isCheckInAvailableAt(LocalTime time) {
        return checkInTime == null || !time.isBefore(checkInTime);
    }
}
