package idusw.sbb.checkin.domain.route.engine;

/**
 * WGS84 위도·경도 좌표 하나. 스프링 의존 없음 — 순수 값 객체.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    public double distanceKmTo(GeoPoint other) {
        return Haversine.distanceKm(this, other);
    }
}
