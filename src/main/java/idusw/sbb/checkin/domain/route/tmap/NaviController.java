package idusw.sbb.checkin.domain.route.tmap;

import idusw.sbb.checkin.domain.route.tmap.dto.NaviRoute;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private double num(Object o) {
        if (o == null) throw new IllegalArgumentException("좌표가 필요합니다.");
        return Double.parseDouble(o.toString());
    }
}
