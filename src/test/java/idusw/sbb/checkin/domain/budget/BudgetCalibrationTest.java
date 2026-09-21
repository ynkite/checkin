package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.BudgetCalibrationService.Sample;
import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate.Calibration;
import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예산 학습 보정 — 적은 표본으로는 흔들지 않고, 튀는 값에는 끌려가지 않는다.
 */
class BudgetCalibrationTest {

    private static Sample busan(String type, boolean peak, long predicted, long actual) {
        return new Sample("부산", type, peak, predicted, actual);
    }

    @Test
    void 표본이_3건_미만이면_보정하지_않는다() {
        List<Sample> s = List.of(busan("호텔", false, 100_000, 150_000), busan("호텔", false, 100_000, 140_000));

        Calibration c = BudgetCalibrationService.calibrate(s, "부산", "호텔", false);

        assertThat(c.applied()).isFalse();
        assertThat(c.multiplier()).isEqualTo(1.0);
        assertThat(c.samples()).isEqualTo(2);
    }

    @Test
    void 표본이_충분하면_중앙값으로_보정한다() {
        List<Sample> s = List.of(
                busan("호텔", false, 100_000, 105_000),
                busan("호텔", false, 100_000, 108_000),
                busan("호텔", false, 100_000, 120_000));

        Calibration c = BudgetCalibrationService.calibrate(s, "부산", "호텔", false);

        assertThat(c.applied()).isTrue();
        assertThat(c.multiplier()).isEqualTo(1.08);
        assertThat(c.samples()).isEqualTo(3);
        assertThat(c.group()).isEqualTo("부산·호텔·비수기");
    }

    @Test
    void 상한과_하한에서_자른다() {
        List<Sample> over = List.of(
                busan(null, false, 100_000, 200_000),
                busan(null, false, 100_000, 180_000),
                busan(null, false, 100_000, 190_000));
        List<Sample> under = List.of(
                busan(null, false, 100_000, 50_000),
                busan(null, false, 100_000, 60_000),
                busan(null, false, 100_000, 70_000));

        Calibration hi = BudgetCalibrationService.calibrate(over, "부산", null, false);
        Calibration lo = BudgetCalibrationService.calibrate(under, "부산", null, false);

        assertThat(hi.multiplier()).isEqualTo(BudgetCalibrationService.MAX);
        assertThat(hi.raw()).isEqualTo(1.9);
        assertThat(lo.multiplier()).isEqualTo(BudgetCalibrationService.MIN);
        assertThat(lo.raw()).isEqualTo(0.6);
    }

    @Test
    void 좁은_묶음이_모자라면_시도까지_넓힌다() {
        List<Sample> s = List.of(
                busan("펜션", true, 100_000, 110_000),
                busan("호텔", false, 100_000, 110_000),
                busan(null, false, 100_000, 110_000),
                new Sample("제주", "호텔", false, 100_000, 300_000));

        Calibration c = BudgetCalibrationService.calibrate(s, "부산", "호텔", false);

        assertThat(c.applied()).isTrue();
        assertThat(c.group()).isEqualTo("부산");
        assertThat(c.samples()).isEqualTo(3);
        assertThat(c.multiplier()).isEqualTo(1.1);
    }

    @Test
    void 지역을_모르면_보정하지_않는다() {
        assertThat(BudgetCalibrationService.calibrate(List.of(), null, "호텔", false).applied()).isFalse();
    }

    @Test
    void 시연_여행과_실측_없는_여행은_표본이_아니다() {
        TravelPlan real = plan("부산 여행"), demo = plan("경주 1박 2일 · 시연"), noActual = plan("부산 둘째");

        List<Sample> s = BudgetCalibrationService.samples(List.of(
                expense(real, 100_000, true), expense(real, 110_000, false),
                expense(demo, 100_000, true), expense(demo, 120_000, false),
                expense(noActual, 100_000, true)));

        assertThat(s).containsExactly(new Sample("부산", null, false, 100_000, 110_000));
    }

    private static TravelPlan plan(String title) {
        return TravelPlan.builder().title(title).destination("부산")
                .startDate(LocalDate.of(2026, 9, 15)).endDate(LocalDate.of(2026, 9, 16)).build();
    }

    private static Expense expense(TravelPlan p, long amount, boolean estimated) {
        return Expense.builder().plan(p).category("STAY").amount(amount)
                .isEstimated(estimated).expenseDate(p.getStartDate()).build();
    }
}
