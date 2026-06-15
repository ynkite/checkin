package idusw.sbb.triplinker.domain.expense.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.triplinker.domain.expense.dto.ExpenseAddRequestDto;
import idusw.sbb.triplinker.domain.expense.service.ExpenseService;
import idusw.sbb.triplinker.global.common.ApiResponse;
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
}