package idusw.sbb.triplinker.domain.expense.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class BudgetReportResponseDto {
    private String currentPageCategory;
    private Long categoryTotalAmount; // 스트림 연산으로 합산 처리할 지출 총액 통계
    private List<ExpenseDetailDto> expenses;

    @Getter
    @Builder
    public static class ExpenseDetailDto {
        private Long id;
        private Long amount;
        private String memo;
        private String date;
    }
}