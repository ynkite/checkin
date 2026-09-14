package idusw.sbb.checkin.domain.expense.controller;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.checkin.domain.expense.dto.ExpenseAddRequestDto;
import idusw.sbb.checkin.domain.expense.service.ExpenseService;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
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