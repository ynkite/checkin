package idusw.sbb.checkin.domain.route.tmap.dto;

import java.util.List;

// 주행 네비게이션용 경로 — 경로선 좌표 + 턴 안내점 + 구간 정보 + 안전 지점.
// 티맵 자차 경로 원응답(carRoute)에서 뽑아 화면이 이탈 판정·턴바이턴에 쓴다.
public record NaviRoute(
        boolean ready,
        List<double[]> line,     // 경로선 [lat,lng] 점들 — 이탈 판정에 쓴다
        List<Guide> guides,      // 턴 안내점
        int totalMeters,
        int totalSeconds,
        String note,
        List<Section> sections,  // LineString feature 하나당 하나. guides.sectionIdx 가 가리킨다
        List<Alert> alerts,      // 경로 위 안전 지점. 없으면 빈 배열
        String alertSource       // alerts 가 어디서 온 것인지 한 줄
) {
    // 한 턴 안내점
    public record Guide(
            double lat,
            double lng,
            String description,  // "해운대로 방면으로 우회전"
            int turnType,        // 회전 종류 코드
            String pointType,
            String name,         // 교차로 이름 ("대남 교차로"). 없으면 ""
            String nextRoadName, // 이 안내 뒤에 올라탈 도로 이름. 없으면 ""
            int meterFromStart,  // 출발점부터 이 안내점까지 누적 거리(m)
            int legMeters,       // 이 안내 뒤 구간 길이(m)
            int sectionIdx       // 이 안내 뒤 구간이 sections 의 몇 번째인가. 없으면 -1
    ) {}

    /** 경로를 도로 단위로 쪼갠 한 조각. 티맵 LineString feature 하나에 대응한다. */
    public record Section(
            String roadName,
            int roadType,        // 티맵 도로 등급 코드
            int facilityType,    // 티맵 시설물 코드 (0 일반 · 1 교량 · 2 터널 · 3 고가도로 · 4 지하차도)
            int meters,
            int seconds,
            int fromMeter,       // 출발점 기준 누적 거리 — 구간 시작
            int toMeter,         // 구간 끝
            Integer speedLimit,  // km/h. 모르면 null
            String speedSource,  // "티맵 도로정보" | "도로등급 추정" | "없음"
            Integer lanes        // 차선 수. 티맵 도로정보에서 같이 온다. 모르면 null
    ) {}

    /** 경로 위에서 미리 알려 줘야 하는 지점. */
    public record Alert(
            String kind,         // school · bump · camera · silver · tunnel · bridge · underpass · overpass
            double lat,
            double lng,
            int meterFromStart,
            String name,         // "해운대초등학교 어린이보호구역". 없으면 ""
            Integer speedLimit,  // km/h. 모르면 null
            String note          // 짧은 한 줄. 없으면 ""
    ) {}

    // 키 없거나 경로 못 받았을 때 — 0으로 채우지 않고 준비중으로 돌려준다
    public static NaviRoute notReady(String note) {
        return new NaviRoute(false, List.of(), List.of(), 0, 0, note, List.of(), List.of(), "연동 전");
    }
}
