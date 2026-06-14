package idusw.sbb.triplinker.domain.expense.dto;

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