package idusw.sbb.triplinker.domain.expense.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ExpenseAddRequestDto {
    private String category;    // STAY, FOOD, TOUR, CAFE
    private String description;
    private Long amount;
    private LocalDate expenseDate;
}