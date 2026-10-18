package idusw.sbb.checkin.domain.expense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.budget.BudgetFitService;
import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 고치기 전에 저장된 2박 일정 — 숙소 줄 4개에 전부 값이 달려 있다(여행 43).
 * 저장 데이터는 그대로 두고, 읽는 쪽(엔티티 getter)에서 거르면 가계부·예산 맞춤·지도가 다 같이 2박이 된다.
 */
class StoredStayDoubleCountTest {

    private static final String SAVED = """
        [{"day":1,"places":[{"type":"tour","name":"동백섬","sub":"관광지"},{"transit":"🚗 자차 · 3.0km · 약 10분 · ₩600"},
                            {"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"}]},
         {"day":2,"places":[{"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"},{"transit":"🚗 자차 · 2.0km · 약 5분 · ₩400"},
                            {"type":"food","name":"밀면","sub":"맛집 · 점심 · ₩9,000×2"},{"transit":"이동"},
                            {"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"}]},
         {"day":3,"places":[{"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"},{"transit":"이동"},
                            {"type":"tour","name":"부산역","sub":"관광지 · 귀가"}]}]
        """;

    private static TravelPlan plan() {
        return TravelPlan.builder().routeJson(SAVED)
                .startDate(LocalDate.of(2026, 10, 24)).endDate(LocalDate.of(2026, 10, 26)).build();
    }

    @Test
    void 저장된_일정을_읽으면_숙박은_밤_수만큼만() throws Exception {
        String json = plan().getRouteJson();
        assertEquals(240_000, BudgetEstimator.scheduleLodging(json), "숙박비 근거 카드");
        assertEquals(240_000 + 18_000 + 1_000, BudgetFitService.total(new ObjectMapper().readTree(json)), "예산 맞춤 총액");
    }

    @Test
    void 가계부_예상_지출도_같은_글에서_나온다() {
        TravelPlan p = plan();
        List<Expense> rows = EstimatedExpenses.from(p, p.getRouteJson());
        long stay = rows.stream().filter(e -> "STAY".equals(e.getCategory())).mapToLong(Expense::getAmount).sum();
        assertEquals(240_000, stay);
        assertEquals(2, rows.stream().filter(e -> "STAY".equals(e.getCategory())).count());
        assertEquals(1_000, rows.stream().filter(e -> "TRANSPORT".equals(e.getCategory())).mapToLong(Expense::getAmount).sum());
    }
}
