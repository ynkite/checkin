package idusw.sbb.checkin.domain.expense.controller;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.expense.dto.BudgetAccuracy;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.checkin.domain.expense.dto.ExpenseAddRequestDto;
import idusw.sbb.checkin.domain.expense.service.ExpenseService;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping("/{tripId}/expenses")
    public ResponseEntity<ApiResponse<BudgetReportResponseDto>> getBudgetReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tripId) {

        BudgetReportResponseDto report = expenseService.getBudgetReport(tripId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /** 예측 vs 실측 오차 — 사용자의 여행 전체 누적 (예산 엔진 3층). */
    @GetMapping("/budget-accuracy")
    public ResponseEntity<ApiResponse<BudgetAccuracy>> budgetAccuracy(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 내 여행의 지출 금액이다. 매처 순서가 흔들려도 익명 요청이 들어오지 않게 한 번 더 막는다
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(ApiResponse.success(
                expenseService.budgetAccuracy(userDetails.getUserId())));
    }

    /** 숙박비 예측 — 항목별 확정/추정 + 신뢰도 (예산 엔진 2층). */
    @GetMapping("/{tripId}/budget-estimate")
    public ResponseEntity<ApiResponse<BudgetEstimate>> budgetEstimate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tripId) {

        return ResponseEntity.ok(ApiResponse.success(expenseService.estimateBudget(tripId)));
    }

    @PostMapping("/{tripId}/expenses")
    public ResponseEntity<ApiResponse<Void>> addExpense(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tripId,
            @RequestBody ExpenseAddRequestDto dto) {

        expenseService.addExpense(tripId, dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{tripId}/expenses/{expenseId}")
    public ResponseEntity<ApiResponse<Void>> updateExpense(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tripId,
            @PathVariable Long expenseId,
            @RequestBody ExpenseAddRequestDto dto) {

        expenseService.updateExpense(expenseId, dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    //실제 지출 삭제
    @DeleteMapping("/{tripId}/expenses/{expenseId}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long tripId,
            @PathVariable Long expenseId) {

        expenseService.deleteExpense(expenseId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}