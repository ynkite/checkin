package idusw.sbb.triplinker.domain.plan.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;  // ← LoginUser 대신
import idusw.sbb.triplinker.domain.plan.dto.PlanCreateDto;
import idusw.sbb.triplinker.domain.plan.dto.PlanDetailResponseDto;
import idusw.sbb.triplinker.domain.plan.dto.PlanInputFormSaveDto;
import idusw.sbb.triplinker.domain.plan.service.TravelPlanService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TravelPlanController {

    private final TravelPlanService travelPlanService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createPlan(
            @AuthenticationPrincipal CustomUserDetails userDetails,  // ← 수정
            @Valid @RequestBody PlanCreateDto dto) {

        Long tripId = travelPlanService.createPlan(userDetails.getUserId(), dto);  // ← 수정
        return ResponseEntity.ok(ApiResponse.success(Map.of("tripId", tripId)));
    }

    @PostMapping("/{tripId}/input-form")
    public ResponseEntity<ApiResponse<Map<String, Long>>> saveInputForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,  // ← 수정
            @PathVariable Long tripId,
            @RequestBody PlanInputFormSaveDto dto) {

        Long formId = travelPlanService.saveInputForm(userDetails.getUserId(), tripId, dto);  // ← 수정
        return ResponseEntity.ok(ApiResponse.success(Map.of("formId", formId)));
    }
    @PatchMapping("/{tripId}/input-form")
    public ResponseEntity<ApiResponse<Void>> updateInputForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tripId,
            @RequestBody Map<String, String> fields) {

        travelPlanService.updateInputForm(userDetails.getUserId(), tripId, fields);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{tripId}/input-form")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getInputForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tripId) {

        java.util.Map<String, Object> data = travelPlanService.getInputFormMap(tripId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<idusw.sbb.triplinker.domain.plan.dto.TripListResponseDto>>> getMyPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<idusw.sbb.triplinker.domain.plan.dto.TripListResponseDto> plans = travelPlanService.getMyTripList(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(plans));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<ApiResponse<PlanDetailResponseDto>> getPlanDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,  // ← 수정
            @PathVariable Long tripId) {

        PlanDetailResponseDto detail = travelPlanService.getPlanDetail(userDetails.getUserId(), tripId);  // ← 수정
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PostMapping("/{tripId}/load-preference")
    public ResponseEntity<ApiResponse<Map<String, Long>>> loadPreviousPreference(
            @AuthenticationPrincipal CustomUserDetails userDetails,  // ← 수정
            @PathVariable Long tripId) {

        Long formId = travelPlanService.loadPreviousPreference(userDetails.getUserId(), tripId);  // ← 수정
        return ResponseEntity.ok(ApiResponse.success(formId != null ? Map.of("formId", formId) : null));
    }

    @GetMapping("/latest-preference")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getLatestPreference(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        java.util.Map<String, Object> result = travelPlanService.getLatestPreference(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 이메일 초대 편집자용 플랜 화면 매핑
    @GetMapping("/plan")
    public String editPlanShare(@RequestParam(value = "id", required = false) Long id, Model model) {
        if (id != null) {
            model.addAttribute("tripId", id);
        }
        return "index";
    }

    // 링크 복사 읽기 전용 뷰 화면 매핑
    @GetMapping("/plan/view")
    public String viewPlanShare(@RequestParam(value = "id", required = false) Long id, Model model) {
        if (id != null) {
            model.addAttribute("tripId", id);
        }
        return "index";
    }

    @PostMapping("/invited")
    public ResponseEntity<ApiResponse<Void>> saveInvitedLink(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> payload) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
        }

        String inviteUrl = payload.get("inviteUrl");
        String title     = payload.get("title");
        String dest      = payload.get("destination");

        travelPlanService.saveInvitedPlan(userDetails.getUserId(), inviteUrl, title, dest);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/invited")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getInvitedLinks(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(java.util.Collections.emptyList()));
        }

        List<Map<String, String>> dataList = travelPlanService.getInvitedPlans(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(dataList));
    }

}