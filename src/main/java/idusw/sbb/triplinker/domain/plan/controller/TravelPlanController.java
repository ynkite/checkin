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

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanDetailResponseDto>>> getMyPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails) {  // ← 수정

        List<PlanDetailResponseDto> plans = travelPlanService.getMyPlans(userDetails.getUserId());  // ← 수정
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
}