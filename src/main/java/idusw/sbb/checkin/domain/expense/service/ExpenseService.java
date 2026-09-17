package idusw.sbb.checkin.domain.expense.service;

import idusw.sbb.checkin.domain.expense.dto.BudgetAccuracy;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.checkin.domain.expense.dto.BudgetReportResponseDto.CategoryBudgetDto;
import idusw.sbb.checkin.domain.expense.dto.ExpenseAddRequestDto;
import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.expense.repository.ExpenseRepository;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final BudgetEstimator budgetEstimator;

    /**
     * 숙박비 예측 — 항목별 확정/추정 + 신뢰도 (예산 엔진 2층).
     * 실제 지출(getBudgetReport)과 달리 아직 쓰지 않은 돈의 예측이다.
     */
    public BudgetEstimate estimateBudget(Long planId) {
        TravelPlan plan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 플랜을 찾을 수 없습니다."));
        return budgetEstimator.estimate(plan);
    }

    /**
     * 예측 vs 실측 검증 루프 (예산 엔진 3층).
     * 새 테이블 없이 가계부의 예상/실제 두 줄만 여행별로 접는다.
     * 한쪽이라도 비어 있으면 오차가 아니라 미입력이므로 표본에서 뺀다.
     */
    public BudgetAccuracy budgetAccuracy(Long userId) {
        // ponytail: 여행 수만큼 plan 을 지연 로딩한다. 한 사용자의 여행 수는 두 자리라 그냥 둔다
        LocalDate today = LocalDate.now();
        Map<Long, long[]> byPlan = new LinkedHashMap<>();   // planId → {예측, 실측}
        Map<Long, String> titles = new LinkedHashMap<>();

        for (Expense e : expenseRepository.findByUserId(userId)) {
            TravelPlan plan = e.getPlan();
            // 아직 안 끝난 여행은 실측이 미완이다. 진행 중인 여행의 오차는 오차가 아니라 중간 집계다
            // (「다녀온 여행」과 같은 기준 — TravelPlanRepository.findPastTrips)
            if (plan.getEndDate() == null || !plan.getEndDate().isBefore(today)) continue;

            Long planId = plan.getId();
            titles.putIfAbsent(planId, plan.getTitle());
            long[] sum = byPlan.computeIfAbsent(planId, k -> new long[2]);
            sum[e.isEstimated() ? 0 : 1] += e.getAmount();
        }

        List<BudgetAccuracy.Row> rows = new ArrayList<>();
        byPlan.forEach((planId, sum) -> {
            if (sum[0] > 0 && sum[1] > 0) {
                rows.add(BudgetAccuracy.Row.of(titles.get(planId), sum[0], sum[1]));
            }
        });
        return BudgetAccuracy.of(rows);
    }

    public BudgetReportResponseDto getBudgetReport(Long planId) {
        TravelPlan plan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 플랜을 찾을 수 없습니다."));

        Long budget = (plan.getForm() != null) ? plan.getForm().getBudget() : null;

        List<Expense> expenses = expenseRepository.findByPlanId(planId);

        long totalEst = 0L;
        long totalAct = 0L;
        Map<String, CategoryBudgetDto> categoryMap = new HashMap<>();
        List<BudgetReportResponseDto.ExpenseDetailDto> estimatedExpenses = new ArrayList<>();
        List<BudgetReportResponseDto.ExpenseDetailDto> actualExpenses    = new ArrayList<>();

        for (Expense expense : expenses) {
            String category = expense.getCategory();
            long   amount   = expense.getAmount();

            categoryMap.putIfAbsent(category, CategoryBudgetDto.builder()
                    .category(category)
                    .estimatedAmount(0L)
                    .actualAmount(0L)
                    .build());

            CategoryBudgetDto categoryDto = categoryMap.get(category);

            BudgetReportResponseDto.ExpenseDetailDto detail = BudgetReportResponseDto.ExpenseDetailDto.builder()
                    .id(expense.getId())
                    .category(category)
                    .description(expense.getDescription())
                    .amount(amount)
                    .date(expense.getExpenseDate().toString())
                    .build();

            if (expense.isEstimated()) {
                categoryDto.addEstimated(amount);
                totalEst += amount;
                estimatedExpenses.add(detail);
            } else {
                categoryDto.addActual(amount);
                totalAct += amount;
                actualExpenses.add(detail);
            }
        }

        return BudgetReportResponseDto.builder()
                .budget(budget)
                .tripTitle(plan.getTitle())
                .destination(plan.getDestination())
                .startDate(plan.getStartDate() != null ? plan.getStartDate().toString() : null)
                .endDate(plan.getEndDate()   != null ? plan.getEndDate().toString()   : null)
                .totalEstimatedAmount(totalEst)
                .totalActualAmount(totalAct)
                .categoryBudgets(new ArrayList<>(categoryMap.values()))
                .estimatedExpenses(estimatedExpenses)
                .actualExpenses(actualExpenses)
                .build();
    }

    @Transactional
    public void addExpense(Long planId, ExpenseAddRequestDto dto) {
        TravelPlan plan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 플랜을 찾을 수 없습니다."));

        Expense expense = Expense.builder()
                .plan(plan)
                .user(plan.getUser())
                .category(dto.getCategory())
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .isEstimated(false)
                .expenseDate(dto.getExpenseDate() != null ? dto.getExpenseDate() : LocalDate.now())
                .build();

        expenseRepository.save(expense);
    }

    @Transactional
    public void updateExpense(Long expenseId, ExpenseAddRequestDto dto) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("지출 항목을 찾을 수 없습니다."));

        expense.update(
                dto.getCategory(),
                dto.getDescription(),
                dto.getAmount(),
                dto.getExpenseDate() != null ? dto.getExpenseDate() : expense.getExpenseDate()
        );
    }

    //실제 지출 삭제
    @Transactional
    public void deleteExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("지출 항목을 찾을 수 없습니다."));
        expenseRepository.delete(expense);
    }
}
