package idusw.sbb.triplinker.domain.expense.service;

import idusw.sbb.triplinker.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.triplinker.domain.expense.dto.BudgetReportResponseDto.CategoryBudgetDto;
import idusw.sbb.triplinker.domain.expense.dto.ExpenseAddRequestDto;
import idusw.sbb.triplinker.domain.expense.entity.Expense;
import idusw.sbb.triplinker.domain.expense.repository.ExpenseRepository;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final TravelPlanRepository travelPlanRepository;

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
