package idusw.sbb.checkin.domain.expense;

import idusw.sbb.checkin.domain.expense.dto.BudgetAccuracy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 3층 검증 루프의 산수만 본다. 화면에 찍히는 오차와 같은 식에서 나오는지. */
class BudgetAccuracyTest {

    @Test
    @DisplayName("오차는 예측 대비 실제의 초과분이다")
    void errorIsOverspendAgainstPrediction() {
        assertThat(BudgetAccuracy.Row.of("부산", 198_000, 205_000).errorPct()).isEqualTo(3.5);
    }

    @Test
    @DisplayName("덜 쓰면 오차는 음수로 남는다 — 절대값으로 감추지 않는다")
    void underspendStaysNegative() {
        assertThat(BudgetAccuracy.Row.of("제주", 200_000, 180_000).errorPct()).isEqualTo(-10.0);
    }

    @Test
    @DisplayName("누적 평균은 부호를 상쇄하지 않는 절대값 평균이다")
    void averageUsesAbsoluteValues() {
        BudgetAccuracy a = BudgetAccuracy.of(List.of(
                BudgetAccuracy.Row.of("부산", 100_000, 110_000),   // +10%
                BudgetAccuracy.Row.of("제주", 100_000,  90_000))); // -10%
        assertThat(a.avgErrorPct()).isEqualTo(10.0);
        assertThat(a.n()).isEqualTo(2);
    }

    @Test
    @DisplayName("표본이 없으면 정확도를 주장하지 않는다")
    void emptySampleClaimsNothing() {
        BudgetAccuracy a = BudgetAccuracy.of(List.of());
        assertThat(a.n()).isZero();
        assertThat(a.note()).contains("시연");
    }
}
