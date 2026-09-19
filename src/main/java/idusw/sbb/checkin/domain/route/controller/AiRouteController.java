package idusw.sbb.checkin.domain.route.controller;

import idusw.sbb.checkin.domain.route.dto.RouteDelta;
import idusw.sbb.checkin.domain.route.service.AiRouteService;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@lombok.extern.slf4j.Slf4j
@RequestMapping("/api/trips/{tripId}/routes")
@RequiredArgsConstructor
public class AiRouteController {

    private final AiRouteService aiRouteService;
    private final idusw.sbb.checkin.domain.route.RouteProgress progress;

    /**
     * 일정을 만드는 데 어디까지 왔나.
     *
     * 만드는 데 30초 넘게 걸린다. 화면이 덮개만 씌우고 기다리게 하지 않으려면
     * 진행을 물어볼 자리가 있어야 한다. 남은 시간은 알려주지 않는다 —
     * 카카오와 모델이 얼마나 걸릴지 미리 알 수 없어서 지어낼 수밖에 없기 때문이다.
     * 끝난 단계만 말한다.
     */
    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<Object>> routeProgress(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success(progress.of(tripId)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Object>> generateRoute(@PathVariable Long tripId) {
        progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.QUEUED);
        try {
        // 1) AI가 type별 장소 후보(이름+sub+stars)만 생성 — 좌표·순서·날짜 없음
        String candidatesJson = aiRouteService.generateCandidates(tripId);

        // 2) 후보 → 좌표 확보 → 일자별 클러스터링 → 시간 골격 조립
        progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.COLLECTING);
        String routeJson = aiRouteService.assembleCandidates(tripId, candidatesJson);
        progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.ASSEMBLING);

        /* 빈 일정을 성공으로 돌려주지 않는다. 전에는 조립이 실패해도 "[]" 가
           그대로 저장되고 성공으로 올라가서, 화면은 4단계로 넘어간 뒤 빈 지도를
           보여 줬다. 사용자는 왜 안 되는지 알 수 없었고 있던 일정까지 지워졌다. */
        if (!idusw.sbb.checkin.domain.route.RouteJson.usable(routeJson)) {
            log.warn("[동선 생성] 들를 곳이 하나도 없어 저장하지 않습니다. tripId={}", tripId);
            progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.FAILED,
                    "조건에 맞는 장소를 찾지 못했습니다");
            return ResponseEntity.ok(ApiResponse.error(
                    "조건에 맞는 장소를 찾지 못했습니다. 지역이나 기간을 조금 바꿔 다시 해 보세요."));
        }

        // 3) DB 저장 (카카오 transit 보정 + 당일치기 숙소 차단 포함)
        progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.SAVING);
        aiRouteService.saveAiRouteToDb(tripId, routeJson);

        // 4) 최종 정리(코드 기반): 한 방향 정렬 + 먼 장소/밀도초과 삭제 (AI 생성·추가 없음)
        //    - 사용자요청인데 숙소에서 먼 장소는 삭제하지 않고 over50으로 반환 → 프론트 알림
        progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.POLISHING);
        java.util.List<String> over50 = aiRouteService.finalizeRoute(tripId);

        // 최종본을 다시 읽어서 반환
        Object finalRoute = aiRouteService.getRoutesByTripId(tripId);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("route", finalRoute);
        data.put("over50", over50);
        data.put("needConfirm", !over50.isEmpty());

        progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.DONE);
        return ResponseEntity.ok(ApiResponse.success(data));

        } catch (RuntimeException e) {
            progress.set(tripId, idusw.sbb.checkin.domain.route.RouteProgress.Phase.FAILED);
            throw e;
        }
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

        // 덮어쓰기 전의 동선 — 교체 결과와 비교해 시간·돈 쌍을 낸다 (예산 엔진 2층)
        String beforeRouteJson = currentRouteJson(tripId);

        // 위에서 만든 서비스 메서드 호출
        String updatedRouteJson = aiRouteService.replaceAiRoutePlaces(tripId, requests);
        // 새로 받아온 JSON을 DB에 덮어쓰고 반환
        aiRouteService.saveAiRouteToDb(tripId, updatedRouteJson);

        // 저장 과정에서 카카오가 구간 시간·요금을 다시 채운다 — 저장된 것끼리 비교해야 맞다
        String afterRouteJson = currentRouteJson(tripId);

        return ResponseEntity.ok(
                java.util.Map.of("success", true, "data", updatedRouteJson,
                        "delta", RouteDelta.between(beforeRouteJson, afterRouteJson))
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

            // 순서를 바꾸는 것도 동선 변경이다 — 저장 전 동선을 들고 있는다 (예산 엔진 2층)
            String beforeRouteJson = currentRouteJson(tripId);

            // 저장(내부에서 카카오 거리/시간/비용 재계산 + budget 갱신 수행)
            aiRouteService.saveAiRouteToDb(tripId, json);
            // 보정된 JSON을 돌려줘서 프론트가 새로고침 없이 화면을 다시 그릴 수 있게 한다
            Object updated = aiRouteService.getRoutesByTripId(tripId);

            String afterRouteJson = currentRouteJson(tripId);

            return ResponseEntity.ok(java.util.Map.of("success", true, "data", updated,
                    "delta", RouteDelta.between(beforeRouteJson, afterRouteJson)));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 지금 화면이 보고 있는 동선 — 시간·돈 쌍의 비교 대상 (예산 엔진 2층).
     *
     * 확정(FIXED)된 여행을 고치면 {@code saveAiRouteToDb} 는 확정본을 안 건드리고
     * {@code draftRouteJson} 에 쓴다. 확정본만 보면 바뀐 게 없다고 나온다.
     * 규칙을 여기서 또 쓰지 않고 화면이 쓰는 것과 같은 메서드를 그대로 쓴다.
     */
    private String currentRouteJson(Long tripId) {
        Object route = aiRouteService.getRoutesByTripId(tripId);
        return (route instanceof String s) ? s : null;
    }
}
