package idusw.sbb.checkin.domain.expense.service;

import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate.Item;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate.Status;
import idusw.sbb.checkin.domain.tour.dto.LodgingRate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 예산 엔진 계산 검증. 숫자가 틀리면 끝이라 화면보다 여기가 먼저다.
 * 화면에 뜨는 신뢰도와 실제 계산이 같은 식에서 나오는지를 본다.
 */
class BudgetEstimatorTest {

    private static Item item(BudgetEstimate e, String labelPrefix) {
        return e.items().stream()
                .filter(i -> i.label().startsWith(labelPrefix))
                .findFirst().orElseThrow();
    }

    @Test
    void 신뢰도는_확정_항목의_금액_비중이다() {
        // 확정 객실료 60,000 x 2박 = 120,000 / 인원 추가 2명 x 20,000 x 2박 = 80,000
        BudgetEstimate e = BudgetEstimator.compute(new BudgetEstimator.Input(
                LodgingRate.confirmed(60_000, "비수기 주중"),
                2, 4, 2, false, false, false, false));

        assertEquals(200_000, e.total());
        assertEquals(60, e.confidence(), "확정 120,000 ÷ 총액 200,000");
        assertEquals(Status.CONFIRMED, item(e, "객실 기본료").status());
        assertEquals(Status.ESTIMATED, item(e, "인원 추가").status());
    }

    @Test
    void 예측_구간은_추정_항목에만_붙는다() {
        BudgetEstimate e = BudgetEstimator.compute(new BudgetEstimator.Input(
                LodgingRate.confirmed(100_000, "비수기 주중"),
                1, 2, 2, true, true, false, false));   // 확정 100,000 + 바베큐 추정 30,000

        assertEquals(130_000, e.total());
        assertEquals(124_000, e.low(),  "추정 30,000 의 20% 인 6,000 만 흔들린다");
        assertEquals(136_000, e.high());
        assertEquals(76, e.confidence(), "100,000 ÷ 130,000 = 76.9% — 내림. 신뢰도는 올려 말하지 않는다");
    }

    @Test
    void 공개요금이_없으면_확정이_0이고_신뢰도도_0이다() {
        BudgetEstimate e = BudgetEstimator.compute(new BudgetEstimator.Input(
                null, 2, 2, 0, false, false, false, false));

        assertEquals(0, e.total());
        assertEquals(0, e.confidence());
        assertEquals(Status.NONE, item(e, "객실 기본료").status());
        assertEquals("공개 요금 없음", item(e, "객실 기본료").basis());
    }

    @Test
    void 지역평균으로_받은_요금은_추정으로_남는다() {
        BudgetEstimate e = BudgetEstimator.compute(new BudgetEstimator.Input(
                LodgingRate.estimated(90_000, 12, "성수기 주말"),
                1, 2, 2, false, false, false, false));

        assertEquals(Status.ESTIMATED, item(e, "객실 기본료").status());
        assertEquals(0, e.confidence(), "추정뿐이면 신뢰도 0");
        assertTrue(item(e, "객실 기본료").basis().contains("N=12"));
    }

    @Test
    void 정원_이내거나_고르지_않은_항목은_해당없음으로_남긴다() {
        BudgetEstimate e = BudgetEstimator.compute(new BudgetEstimator.Input(
                LodgingRate.confirmed(80_000, "비수기 주중"),
                1, 2, 4, false, false, false, false));

        assertEquals(80_000, e.total());
        assertEquals(Status.NONE, item(e, "인원 추가").status());
        assertEquals(Status.NONE, item(e, "바베큐").status());
        assertEquals(Status.NONE, item(e, "반려동물").status());
        assertEquals(5, e.items().size(), "해당없음 항목도 화면에서 지우지 않는다");
    }

    @Test
    void 바베큐는_제공확인이어도_금액은_추정이다() {
        BudgetEstimate e = BudgetEstimator.compute(new BudgetEstimator.Input(
                LodgingRate.confirmed(80_000, "비수기 주중"),
                1, 2, 2, true, true, false, false));

        Item bbq = item(e, "바베큐");
        assertEquals(Status.ESTIMATED, bbq.status());
        assertTrue(bbq.basis().contains("숙소 제공 확인"));
        assertTrue(bbq.basis().contains("추정"));
    }

    @Test
    void 동선에서_첫_숙소_이름을_뽑는다() {
        String routeJson = """
            [{"day":1,"places":[
               {"type":"tour","name":"감천문화마을"},
               {"type":"stay","name":"파라다이스 호텔 부산"}]}]
            """;
        assertEquals("파라다이스 호텔 부산", BudgetEstimator.stayName(routeJson));
        assertNull(BudgetEstimator.stayName("깨진 JSON"));
        assertNull(BudgetEstimator.stayName(null));
    }
}
