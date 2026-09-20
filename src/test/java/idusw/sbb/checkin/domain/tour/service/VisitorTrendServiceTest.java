package idusw.sbb.checkin.domain.tour.service;

import idusw.sbb.checkin.domain.tour.dto.VisitorCount;
import idusw.sbb.checkin.domain.tour.service.VisitorTrendService.Status;
import idusw.sbb.checkin.domain.tour.service.VisitorTrendService.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** 지역 방문자 — 외지인만, 같은 요일끼리 견준다. 값이 없으면 없다고 말한다 */
class VisitorTrendServiceTest {

    private final VisitorService visitor = mock(VisitorService.class);
    private final VisitorTrendService svc = new VisitorTrendService(visitor);

    private VisitorCount row(String date, String area, String dow, String type, double n) {
        return new VisitorCount(date, area, area.equals("26") ? "부산광역시" : "서울특별시", dow, type, n);
    }

    @Test
    void 같은_요일_지난_네_번_평균과_견주고_외지인만_센다() {
        when(visitor.daily(any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                row("20260918", "26", "금요일", "외지인", 100),
                row("20260918", "26", "금요일", "현지인", 9999),   // 현지인은 세지 않는다
                row("20260917", "26", "목요일", "외지인", 7777),   // 다른 요일은 기준선에 넣지 않는다
                row("20260911", "26", "금요일", "외지인", 80),
                row("20260904", "26", "금요일", "외지인", 90),
                row("20260828", "26", "금요일", "외지인", 70),
                row("20260821", "26", "금요일", "외지인", 80),
                row("20260918", "11", "금요일", "외지인", 5555)));  // 다른 시도

        Trend t = svc.summary("부산");
        assertThat(t.status()).isEqualTo(Status.OK);
        assertThat(t.areaName()).isEqualTo("부산");
        assertThat(t.latestDate()).isEqualTo("20260918");
        assertThat(t.latest()).isEqualTo(100);
        assertThat(t.baseline()).isEqualTo(80);          // (80+90+70+80)/4
        assertThat(t.deltaPercent()).isEqualTo(25);
        assertThat(t.note()).contains("지난 4번");
    }

    @Test
    void 같은_요일_표본이_없으면_견주지_않고_그렇다고_말한다() {
        when(visitor.daily(any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                row("20260918", "26", "금요일", "외지인", 100)));
        Trend t = svc.summary("부산");
        assertThat(t.status()).isEqualTo(Status.OK);
        assertThat(t.latest()).isEqualTo(100);
        assertThat(t.baseline()).isNull();
        assertThat(t.deltaPercent()).isNull();
        assertThat(t.note()).contains("견줄 기준이 없습니다");
    }

    @Test
    void 못_받으면_UNAVAILABLE_받았는데_그_지역이_없으면_NO_DATA() {
        when(visitor.daily(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        assertThat(svc.summary("부산").status()).isEqualTo(Status.UNAVAILABLE);

        when(visitor.daily(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row("20260918", "11", "금요일", "외지인", 10)));
        Trend t = svc.summary("부산");
        assertThat(t.status()).isEqualTo(Status.NO_DATA);
        assertThat(t.note()).contains("올라와 있지 않습니다");
    }

    @Test
    void 지역_코드를_모르는_여행지는_부르지_않는다() {
        assertThat(svc.summary("아틀란티스").status()).isEqualTo(Status.NO_AREA);
        verifyNoInteractions(visitor);
    }
}
