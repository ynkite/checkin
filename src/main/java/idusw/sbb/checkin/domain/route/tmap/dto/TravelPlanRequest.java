package idusw.sbb.checkin.domain.route.tmap.dto;

import java.util.List;

/**
 * 출발지 + 들를 곳들 -> 구간별 이동시간 요청.
 *
 * 출발지는 좌표(originLat/Lng)나 글자(originName) 중 하나만 있으면 된다.
 * 글자만 오면 TMAP POI 검색으로 좌표를 찾는다 — 사용자는 「부산역」이라고 쓴다.
 *
 * departAt 은 첫 구간의 출발 시각(ISO). 있으면 그 시각의 교통량으로 계산한다.
 * 없으면 지금 기준이다.
 */
public record TravelPlanRequest(
        String  originName,
        Double  originLat,
        Double  originLng,
        String  mode,          // CAR | TRANSIT | WALK. 없으면 CAR
        String  departAt,      // 2026-09-19T14:00:00+0900. 없으면 지금
        List<Stop> stops
) {
    public record Stop(String name, Double lat, Double lng, String time) {}

    public String modeOr(String fallback) {
        return mode == null || mode.isBlank() ? fallback : mode.toUpperCase();
    }
}
