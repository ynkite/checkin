package idusw.sbb.checkin.domain.route.engine;

/**
 * 두 좌표 사이 대권거리(km) 계산. 외부 API 호출 없이 즉시 계산되는
 * 개발용/기본용 거리 추정치 — {@code HaversineTravelTime} 이 이 값을 그대로 쓴다.
 */
public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Haversine() {
    }

    public static double distanceKm(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLng = Math.toRadians(to.longitude() - from.longitude());

        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));

        return EARTH_RADIUS_KM * c;
    }
}
