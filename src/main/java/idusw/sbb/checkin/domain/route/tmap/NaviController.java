package idusw.sbb.checkin.domain.route.tmap;

import idusw.sbb.checkin.domain.route.tmap.dto.NaviRoute;
import idusw.sbb.checkin.domain.route.tmap.dto.OptimizedRoute;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class NaviController {

    private final NaviService naviService;

    // 주행 네비 경로 — 현재위치 → 다음 목적지. 경로선 + 턴 안내점.
    // 이탈 재탐색 시 현재 위치를 다시 넣어 다시 부른다.
    @PostMapping("/navi")
    public ResponseEntity<ApiResponse<NaviRoute>> navi(@RequestBody Map<String, Object> body) {
        double sx = num(body.get("startLng")), sy = num(body.get("startLat"));
        double ex = num(body.get("endLng")),   ey = num(body.get("endLat"));
        String startName = body.get("startName") != null ? body.get("startName").toString() : "현재 위치";
        String endName   = body.get("endName") != null ? body.get("endName").toString() : "목적지";
        NaviRoute route = naviService.driveRoute(sx, sy, ex, ey, startName, endName);
        return ResponseEntity.ok(ApiResponse.success(route));
    }

    // 경유지 최적화 — 여러 목적지를 가장 짧게 도는 순서로 재정렬.
    // body: startLat/Lng, endLat/Lng, startName/endName,
    //       waypoints[{id,name,lat,lng}] (최대 10곳)
    @PostMapping("/optimize")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<OptimizedRoute>> optimize(@RequestBody Map<String, Object> body) {
        double sx = num(body.get("startLng")), sy = num(body.get("startLat"));
        double ex = num(body.get("endLng")),   ey = num(body.get("endLat"));
        String startName = body.get("startName") != null ? body.get("startName").toString() : "출발지";
        String endName   = body.get("endName")   != null ? body.get("endName").toString()   : "도착지";
        List<Map<String, Object>> waypoints = (List<Map<String, Object>>) body.get("waypoints");
        OptimizedRoute route = naviService.optimize(sx, sy, ex, ey, startName, endName, waypoints);
        return ResponseEntity.ok(ApiResponse.success(route));
    }

    // 주변 검색 — 「근처 주유소·화장실·편의점」
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<java.util.List<Map<String, Object>>>> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int radiusKm,
            @RequestParam(defaultValue = "5") int count) {
        return ResponseEntity.ok(ApiResponse.success(
                naviService.nearby(lat, lng, keyword, radiusKm, count)));
    }

    private double num(Object o) {
        if (o == null) throw new IllegalArgumentException("좌표가 필요합니다.");
        return Double.parseDouble(o.toString());
    }
}
