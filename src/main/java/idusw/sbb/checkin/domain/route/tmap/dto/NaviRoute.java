package idusw.sbb.checkin.domain.route.tmap.dto;

import java.util.List;

// 주행 네비게이션용 경로 — 경로선 좌표 + 턴 안내점.
// 티맵 자차 경로 원응답(carRoute)에서 뽑아 화면이 이탈 판정·턴바이턴에 쓴다.
public record NaviRoute(
        boolean ready,
        List<double[]> line,     // 경로선 [lat,lng] 점들 — 이탈 판정에 쓴다
        List<Guide> guides,      // 턴 안내점
        int totalMeters,
        int totalSeconds,
        String note
) {
    // 한 턴 안내점
    public record Guide(
            double lat,
            double lng,
            String description,  // "해운대로 방면으로 우회전"
            int turnType,        // 회전 종류 코드
            String pointType
    ) {}

    // 키 없거나 경로 못 받았을 때 — 0으로 채우지 않고 준비중으로 돌려준다
    public static NaviRoute notReady(String note) {
        return new NaviRoute(false, List.of(), List.of(), 0, 0, note);
    }
}
