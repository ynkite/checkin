package idusw.sbb.checkin.domain.route.tmap.dto;

import java.util.List;

// 경유지 최적화 결과 — 여러 목적지를 가장 짧게 도는 순서 + 경로선.
// 티맵 routeOptimization10 원응답에서 뽑는다. order 는 입력 경유지 id 를
// 최적 방문 순서로 재배열한 것이다.
public record OptimizedRoute(
        boolean ready,
        List<String> order,      // 재정렬된 경유지 id (방문 순서대로)
        List<double[]> line,     // 경로선 [lat,lng] 점들
        int totalMeters,
        int totalSeconds,
        String note
) {
    public static OptimizedRoute notReady(String note) {
        return new OptimizedRoute(false, List.of(), List.of(), 0, 0, note);
    }
}
