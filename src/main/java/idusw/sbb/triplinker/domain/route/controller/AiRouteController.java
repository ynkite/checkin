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
        // 1) AI에게 일정을 짜달라고 요청
        String aiRouteJson = aiRouteService.generateAiRoute(tripId);

        // 2) AI가 준 JSON을 DB에 저장 (saveAiRouteToDb 안에서 카카오 거리 보정도 수행)
        aiRouteService.saveAiRouteToDb(tripId, aiRouteJson);

        // 3) 카카오 실측 거리로 동선 교정: 20/13km 초과(~50km)는 자동 교체,
        //    50km 초과 장소가 있으면 그 목록을 받아 프론트 알림창에 위임
        java.util.List<String> over50 = aiRouteService.enforceDistanceAndGetOver50(tripId, aiRouteJson);

        // 최종본을 다시 읽어서 반환 (자동 교체로 바뀌었을 수 있음)
        Object finalRoute = aiRouteService.getRoutesByTripId(tripId);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("route", finalRoute);
        data.put("over50", over50);          // 비어있으면 알림 없음
        data.put("needConfirm", !over50.isEmpty());

        return ResponseEntity.ok(ApiResponse.success(data));
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
            // 저장(내부에서 카카오 거리/시간/비용 재계산 + budget 갱신 수행)
            aiRouteService.saveAiRouteToDb(tripId, json);
            // 보정된 JSON을 돌려줘서 프론트가 새로고침 없이 화면을 다시 그릴 수 있게 한다
            Object updated = aiRouteService.getRoutesByTripId(tripId);
            return ResponseEntity.ok(java.util.Map.of("success", true, "data", updated));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }


}