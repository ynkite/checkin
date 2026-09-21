package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.budget.dto.BudgetLimitCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BudgetLimitServiceTest {

    private final BudgetLimitService svc = new BudgetLimitService();

    private BudgetEstimate estimate(long total, BudgetEstimate.Item... items) {
        return new BudgetEstimate(total, total, 2, 2, 3, List.of(items), null, List.of(), null, List.of(), null);
    }

    private BudgetEstimate.Item item(String label, long amount) {
        return BudgetEstimate.Item.estimated("STAY", label, amount, "", null);
    }

    @Test
    void 예산을_넘으면_초과액과_가장_큰_항목을_준다() {
        BudgetEstimate est = estimate(320_000,
                item("숙박", 240_000), item("식비", 50_000), item("입장료", 30_000));

        BudgetLimitCheck c = svc.check(est, 300_000);

        assertTrue(c.hasLimit());
        assertTrue(c.over());
        assertEquals(20_000, c.overBy());       // 320,000 - 300,000
        assertEquals("숙박", c.biggestLabel());  // 줄일 후보
        assertEquals(240_000, c.biggestAmount());
    }

    @Test
    void 예산_안이면_초과_아님() {
        BudgetEstimate est = estimate(280_000, item("숙박", 200_000));
        BudgetLimitCheck c = svc.check(est, 300_000);
        assertFalse(c.over());
        assertEquals(0, c.overBy());
    }

    @Test
    void 예산_미설정이면_초과판단_없음() {
        BudgetEstimate est = estimate(500_000, item("숙박", 400_000));
        BudgetLimitCheck c = svc.check(est, 0);   // 예산 안 정함
        assertFalse(c.hasLimit());
        assertFalse(c.over());                    // 0 으로 「초과」를 위장하지 않는다
        assertEquals(0, c.overBy());
    }
}
