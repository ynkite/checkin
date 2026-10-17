package idusw.sbb.checkin.domain.route.tmap;

import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.route.engine.Haversine;

/**
 * 티맵이 답하지 않을 때(하루 한도 · 응답 지연 · 키 없음) 쓰는 예상값.
 * 직선거리에 도로 우회 1.3배를 곱하고 도심 평균 시속 30km 로 나눈다.
 * 화면에는 반드시 「예상」이라고 적는다 — 잰 값처럼 보이면 안 된다.
 */
public final class RouteEstimate {

    public static final String NOTE = "잠시 예상 시간으로 보여 드려요";

    private static final double DETOUR = 1.3;
    private static final double KMH = 30.0;

    private RouteEstimate() {}

    /** @return {미터, 초} */
    public static int[] of(double lat1, double lng1, double lat2, double lng2) {
        double km = Haversine.distanceKm(new GeoPoint(lat1, lng1), new GeoPoint(lat2, lng2)) * DETOUR;
        int sec = (int) Math.round(km / KMH * 3600);
        return new int[]{(int) Math.round(km * 1000), Math.max(60, sec)};
    }
}
