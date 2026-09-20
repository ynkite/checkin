package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지도 예산 항목의 3등급 — 확정 · 추정 · 해당없음.
 *
 * <p>전에는 {@code boolean estimated} 하나였다. 그러면 <b>값이 없는 항목과 추정한 항목이
 * 화면에서 같은 얼굴</b>이 된다. 당일치기 여행의 숙박비는 「0원으로 추정」이 아니라
 * 「이 여행에 없는 항목」이다. 예산은 심사위원이 제일 먼저 의심하는 숫자라 이 구분이 있어야 한다.
 *
 * <p>가계부 쪽 {@code Status} 를 그대로 쓴다 — 두 벌을 만들면 한쪽만 고쳐질 때 화면끼리
 * 어긋난다. 그 <b>같은 타입</b>인지도 여기서 지킨다.
 */
class BudgetItemStatusTest {

    @Test
    void 가계부와_같은_타입을_쓴다() {
        // 두 벌이 되면 한쪽만 고쳐질 때 화면끼리 어긋난다
        BudgetEstimate.Item i = BudgetEstimate.Item.confirmed("FOOD", "식비", 1000, "근거", null);

        assertThat(i.status()).isInstanceOf(Status.class);
        assertThat(Status.values()).containsExactly(Status.CONFIRMED, Status.ESTIMATED, Status.NONE);
    }

    @Test
    void 해당없음은_금액이_0이고_추정이_아니다() {
        BudgetEstimate.Item i = BudgetEstimate.Item.none("LODGING", "숙박", "당일치기라 숙박이 없습니다");

        assertThat(i.status()).isEqualTo(Status.NONE);
        assertThat(i.amount()).isZero();
        assertThat(i.estimated()).as("「없음」은 추정이 아니다").isFalse();
    }

    @Test
    void 확정과_추정이_갈린다() {
        BudgetEstimate.Item c = BudgetEstimate.Item.confirmed("TRANSPORT", "대중교통", 4500, "티맵 실측", null);
        BudgetEstimate.Item e = BudgetEstimate.Item.estimated("TRANSPORT", "대중교통", 4500, "가정", null);

        assertThat(c.status()).isEqualTo(Status.CONFIRMED);
        assertThat(c.estimated()).isFalse();
        assertThat(e.status()).isEqualTo(Status.ESTIMATED);
        assertThat(e.estimated()).isTrue();
    }

    @Test
    void 해당없음은_성수기_배수를_달지_않는다() {
        // 없는 항목에 성수기를 적으면 「있는데 비싸다」로 읽힌다
        assertThat(BudgetEstimate.Item.none("TOUR", "입장료", "들를 곳이 아직 없습니다").seasonApplied())
                .isNull();
    }
}
