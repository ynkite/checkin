package idusw.sbb.triplinker.domain.route.controller;

import idusw.sbb.triplinker.domain.route.service.AiRouteService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/routes")
@RequiredArgsConstructor
public class AiRouteController {

    private final AiRouteService aiRouteService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Object>> generateRoute(@PathVariable Long tripId) {
        // AI에게 일정을 짜달라고 요청
        String aiRouteJson = aiRouteService.generateAiRoute(tripId);

        // AI가 준 JSON을 DB에 저장하는 서비스 호출
        aiRouteService.saveAiRouteToDb(tripId, aiRouteJson);

        return ResponseEntity.ok(ApiResponse.success(aiRouteJson));
    }

    // 컨트롤러에 추가해야 할 GET 매핑 (데이터 반환용)
    @GetMapping("")
    public ResponseEntity<ApiResponse<Object>> getRoutes(@PathVariable Long tripId) {
        // routeService -> aiRouteService 로 수정
        return ResponseEntity.ok(ApiResponse.success(aiRouteService.getRoutesByTripId(tripId)));
    }

    @PostMapping("/replace")
    public ResponseEntity<?> replaceRoutePlaces(
            @PathVariable Long tripId,
            @RequestBody java.util.Map<String, java.util.List<java.util.Map<String, String>>> payload) {

        java.util.List<java.util.Map<String, String>> requests = payload.get("requests");
        // 위에서 만든 서비스 메서드 호출
        String updatedRouteJson = aiRouteService.replaceAiRoutePlaces(tripId, requests);
        // 새로 받아온 JSON을 DB에 덮어쓰고 반환
        aiRouteService.saveAiRouteToDb(tripId, updatedRouteJson);

        return ResponseEntity.ok(
                java.util.Map.of("success", true, "data", updatedRouteJson)
        );
    }

    // 날씨악화 실내 일정 교체 API
    @PostMapping("/indoor-replace")
    public ResponseEntity<?> replaceDayIndoor(
            @PathVariable Long tripId,
            @RequestParam int day) {

        // AI한테 실내 일정으로 교체하라고 시킴
        String newRouteJson = aiRouteService.replaceDayWithIndoor(tripId, day);

        // 바뀐 일정을 DB에 즉시 덮어쓰기 저장
        aiRouteService.saveAiRouteToDb(tripId, newRouteJson);

        // 성공 시 프론트엔드에 새 JSON 내려줌
        return ResponseEntity.ok(
                java.util.Map.of("success", true, "data", newRouteJson)
        );
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> updateRouteManually(
            @PathVariable Long tripId,
            @RequestBody com.fasterxml.jackson.databind.JsonNode routeData) {
        try {
            String json = routeData.toString();
            aiRouteService.saveAiRouteToDb(tripId, json);
            return ResponseEntity.ok(java.util.Map.of("success", true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }


}