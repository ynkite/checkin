package idusw.sbb.triplinker.domain.expense.entity;


import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

//가계부 엔티티
@Entity
@Table(name = "EXPENSES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private TravelPlan plan;

    @Column(nullable = false, length = 20)
    private String category; // STAY, FOOD, TOUR, CAFE

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private Long amount;

    //예상 비용 여부 (0: 실제 지출, 1: AI 생성 예상 비용)
    @Column(name = "is_estimated", nullable = false)
    private boolean isEstimated;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Builder
    public Expense(TravelPlan plan, String category, String description, Long amount, boolean isEstimated, LocalDate expenseDate) {
        this.plan = plan;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.isEstimated = isEstimated;
        this.expenseDate = expenseDate;
    }

    public void update(String category, String description, Long amount, LocalDate expenseDate) {
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.expenseDate = expenseDate;
    }
}