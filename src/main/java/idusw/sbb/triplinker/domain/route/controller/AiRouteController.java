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

    // AiRouteController.java 추가 내용
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Object>> generateRoute(@PathVariable Long tripId) {
        // 1. AI에게 일정을 짜달라고 요청
        String aiRouteJson = aiRouteService.generateAiRoute(tripId);

        // 2. [중요] AI가 준 JSON을 DB에 저장하는 서비스 호출
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
}