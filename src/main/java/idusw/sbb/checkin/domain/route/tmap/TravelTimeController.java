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

    /**
     * 같은 동선을 자차·대중교통·도보로 각각 재서 한 번에 준다.
     * 화면은 셋을 나란히 보여 주고 고른 것을 구간 줄에 적용한다.
     */
    @PostMapping("/travel-time/compare")
    public ResponseEntity<ApiResponse<Map<String, TravelPlan>>> compare(
            @RequestBody TravelPlanRequest req) {
        return ResponseEntity.ok(ApiResponse.success(travelTimeService.compare(req)));
    }

    /**
     * 좌표 -> 주소. 플래너의 「지금 위치」 버튼이 쓴다.
     * 키가 없으면 ready=false 로 답한다 — 화면은 조용히 넘어가고
     * 직접 입력·검색 두 가지를 그대로 쓴다.
     */
    @GetMapping("/reverse")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reverse(
            @RequestParam double lat, @RequestParam double lng) {
        Map<String, Object> out = new LinkedHashMap<>();
        JsonNode r = tmap.reverseGeo(lat, lng);
        if (r == null) {
            out.put("ready", false);
            out.put("note", "좌표를 주소로 바꾸는 연동은 준비 중입니다.");
            return ResponseEntity.ok(ApiResponse.success(out));
        }
        JsonNode a = r.path("addressInfo");
        String full = a.path("fullAddress").asText("");
        String road = a.path("newRoadAddress").asText("");
        out.put("ready", true);
        out.put("lat", lat);
        out.put("lng", lng);
        /* 도로명이 있으면 그걸 쓴다. 사람이 아는 주소는 도로명 쪽이다 */
        out.put("address", !road.isBlank() ? road : full);
        out.put("city", a.path("city_do").asText(""));
        out.put("gu", a.path("gu_gun").asText(""));
        return ResponseEntity.ok(ApiResponse.success(out));
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
