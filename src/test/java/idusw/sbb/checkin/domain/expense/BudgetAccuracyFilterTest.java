package idusw.sbb.checkin.domain.expense;

import idusw.sbb.checkin.domain.expense.dto.BudgetAccuracy;
import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.expense.repository.ExpenseRepository;
import idusw.sbb.checkin.domain.expense.service.BudgetEstimator;
import idusw.sbb.checkin.domain.expense.service.ExpenseService;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 검증 루프가 어떤 여행을 표본에 넣는지 본다 (3층).
 * 진행 중인 여행이 섞이면 실측이 미완인 채로 오차가 찍혀 화면이 거짓말을 한다 — 9/17 실제로 겪었다.
 */
class BudgetAccuracyFilterTest {

    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final ExpenseService service = new ExpenseService(
            expenseRepository, mock(TravelPlanRepository.class), mock(BudgetEstimator.class));

    private TravelPlan plan(long id, String title, LocalDate end) {
        TravelPlan p = TravelPlan.builder()
                .title(title).destination("부산")
                .startDate(end.minusDays(1)).endDate(end)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private Expense expense(TravelPlan plan, long amount, boolean estimated) {
        return Expense.builder()
                .plan(plan).category("STAY").amount(amount)
                .isEstimated(estimated).expenseDate(plan.getStartDate())
                .build();
    }

    @Test
    @DisplayName("끝난 여행만 표본에 넣는다 — 진행 중인 여행의 실측은 미완이다")
    void onlyFinishedTripsCount() {
        TravelPlan past    = plan(1, "지난 여행",   LocalDate.now().minusDays(1));
        TravelPlan ongoing = plan(2, "진행 중 여행", LocalDate.now().plusDays(2));

        when(expenseRepository.findByUserId(7L)).thenReturn(List.of(
                expense(past,    100_000, true), expense(past,    110_000, false),
                expense(ongoing, 300_000, true), expense(ongoing,  50_000, false)));

        BudgetAccuracy a = service.budgetAccuracy(7L);

        assertThat(a.n()).isEqualTo(1);
        assertThat(a.rows().get(0).tripTitle()).isEqualTo("지난 여행");
        assertThat(a.rows().get(0).errorPct()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("실측이 없는 여행은 오차 0이 아니라 표본에서 빠진다")
    void tripWithoutActualIsNotASample() {
        TravelPlan past = plan(1, "실측 미입력", LocalDate.now().minusDays(1));
        when(expenseRepository.findByUserId(7L)).thenReturn(List.of(expense(past, 100_000, true)));

        assertThat(service.budgetAccuracy(7L).n()).isZero();
    }
}
