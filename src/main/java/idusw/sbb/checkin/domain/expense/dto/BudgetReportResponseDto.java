package idusw.sbb.checkin.domain.expense.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class BudgetReportResponseDto {

    private Long   budget;
    private String tripTitle;
    private String destination;
    private String startDate;
    private String endDate;
    private String transportType;   // 자차 · 렌터카 · 대중교통 — 교통비 줄의 안내에 쓴다

    private Long totalEstimatedAmount;
    private Long totalActualAmount;

    private List<CategoryBudgetDto>  categoryBudgets;
    private List<ExpenseDetailDto>   estimatedExpenses;
    private List<ExpenseDetailDto>   actualExpenses;

    @Getter
    @Builder
    public static class CategoryBudgetDto {
        private String category;
        private Long   estimatedAmount;
        private Long   actualAmount;

        public void addEstimated(Long amount) { this.estimatedAmount += amount; }
        public void addActual(Long amount)    { this.actualAmount    += amount; }
    }

    @Getter
    @Builder
    public static class ExpenseDetailDto {
        private Long   id;
        private String category;
        private String description;
        private Long   amount;
        private String date;
    }
}