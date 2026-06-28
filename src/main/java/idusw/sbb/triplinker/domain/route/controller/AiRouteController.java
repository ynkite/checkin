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
        // 1) AI가 type별 장소 후보(이름+sub+stars)만 생성 — 좌표·순서·날짜 없음
        String candidatesJson = aiRouteService.generateCandidates(tripId);

        // 2) 후보 → 좌표 확보 → 일자별 클러스터링 → 시간 골격 조립
        String routeJson = aiRouteService.assembleCandidates(tripId, candidatesJson);

        // 3) DB 저장 (카카오 transit 보정 + 당일치기 숙소 차단 포함)
        aiRouteService.saveAiRouteToDb(tripId, routeJson);

        // 4) 50km 초과 장소 검사: 사용자요청 장소는 알림(over50), AI 장소는 자동교체
        java.util.List<String> over50 = aiRouteService.enforceDistanceAndGetOver50(tripId, routeJson);

        // 5) 동선 순서 검증·교정 (장소명·개수 변경 없이 순서·time만 조정)
        aiRouteService.validateOrderWithClaude(tripId);

        // 최종본을 다시 읽어서 반환
        Object finalRoute = aiRouteService.getRoutesByTripId(tripId);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("route", finalRoute);
        data.put("over50", over50);
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