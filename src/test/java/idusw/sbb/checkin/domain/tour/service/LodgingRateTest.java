package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.LodgingRate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LodgingRateTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 계절_요일로_요금필드가_정확히_선택된다() {
        assertEquals("roompeakseasonminfee2", LodgingRateService.feeField(true, true));    // 성수기 주말
        assertEquals("roompeakseasonminfee1", LodgingRateService.feeField(true, false));   // 성수기 주중
        assertEquals("roomoffseasonminfee2",  LodgingRateService.feeField(false, true));   // 비수기 주말
        assertEquals("roomoffseasonminfee1",  LodgingRateService.feeField(false, false));  // 비수기 주중
    }

    @Test
    void 성수기는_7_8월() {
        assertTrue(LodgingRateService.isPeak(LocalDate.of(2026, 7, 15)));
        assertTrue(LodgingRateService.isPeak(LocalDate.of(2026, 8, 1)));
        assertFalse(LodgingRateService.isPeak(LocalDate.of(2026, 9, 14)));
        assertFalse(LodgingRateService.isPeak(LocalDate.of(2026, 6, 30)));
    }

    @Test
    void 주말은_금토_체크인() {
        assertTrue(LodgingRateService.isWeekend(LocalDate.of(2026, 9, 18)));  // 금요일
        assertTrue(LodgingRateService.isWeekend(LocalDate.of(2026, 9, 19)));  // 토요일
        assertFalse(LodgingRateService.isWeekend(LocalDate.of(2026, 9, 20))); // 일요일
        assertFalse(LodgingRateService.isWeekend(LocalDate.of(2026, 9, 14))); // 월요일
    }

    @Test
    void 공개요금이_있으면_확정으로_돌려준다() {
        // detailInfo2 에 성수기 주말 요금이 채워진 객실 2개 (최솟값 130,000)
        String rooms = """
            [{"roompeakseasonminfee2":"150000"},{"roompeakseasonminfee2":"130000"}]
            """;
        TourApiClient client = mock(TourApiClient.class);
        try { when(client.items(any(), eq("detailInfo2"), any())).thenReturn(om.readTree(rooms)); }
        catch (Exception e) { throw new RuntimeException(e); }

        LodgingRate r = new LodgingRateService(client).nightly("T1", "6", LocalDate.of(2026, 8, 1)); // 성수기 토요일

        assertFalse(r.estimated(), "공개 요금이면 확정");
        assertEquals(130_000, r.amount());     // 가장 싼 방
        assertEquals("성수기 주말", r.season());
        assertEquals("공개 요금", r.basis());
    }
}
