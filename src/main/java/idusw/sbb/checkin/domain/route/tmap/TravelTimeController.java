package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelPlan;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelPlanRequest;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이동시간 — 출발지에서 들르는 순서대로.
 *
 *   POST /api/route/travel-time   구간별 이동시간 + 합계
 *   GET  /api/route/origin?q=     출발지 글자 -> 후보 좌표 (자동완성)
 *   GET  /api/route/status        TMAP 키가 들어와 있는가
 */
@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class TravelTimeController {

    private final TravelTimeService travelTimeService;
    private final TmapClient tmap;

    @PostMapping("/travel-time")
    public ResponseEntity<ApiResponse<TravelPlan>> travelTime(@RequestBody TravelPlanRequest req) {
        return ResponseEntity.ok(ApiResponse.success(travelTimeService.plan(req)));
    }

    /** 출발지 후보. 화면의 「출발지」 칸이 부른다. */
    @GetMapping("/origin")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> origin(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int count) {

        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode res = tmap.pois(q, count);
        if (res != null) {
            JsonNode poi = res.path("searchPoiInfo").path("pois").path("poi");
            if (poi.isArray()) {
                for (JsonNode p : poi) out.add(toPlace(p));
            } else if (poi.isObject()) {
                out.add(toPlace(poi));
            }
        }
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "tmapReady", tmap.ready(),
                "note", tmap.ready() ? "이동시간을 실제로 잽니다."
                        : "TMAP 키가 아직 없습니다. 이동시간은 「확인 중」으로 둡니다."
        )));
    }

    private Map<String, Object> toPlace(JsonNode p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.path("name").asText(""));
        /* 주소는 신주소가 있으면 신주소, 없으면 지번이다.
           둘 다 조각으로 오므로 붙여 준다. */
        String road = join(p.path("upperAddrName").asText(""),
                p.path("middleAddrName").asText(""),
                p.path("roadName").asText(""),
                p.path("firstBuildNo").asText(""));
        String jibun = join(p.path("upperAddrName").asText(""),
                p.path("middleAddrName").asText(""),
                p.path("lowerAddrName").asText(""),
                p.path("firstNo").asText(""));
        m.put("address", !road.isBlank() ? road : jibun);
        m.put("lat", p.path("frontLat").asDouble(p.path("noorLat").asDouble(0)));
        m.put("lng", p.path("frontLon").asDouble(p.path("noorLon").asDouble(0)));
        return m;
    }

    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s == null || s.isBlank() || "0".equals(s)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(s);
        }
        return sb.toString();
    }
}
