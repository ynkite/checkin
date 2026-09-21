package idusw.sbb.checkin.domain.crowd;

import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.tour.dto.ConcentrationRate;
import idusw.sbb.checkin.domain.tour.service.ConcentrationService;
import idusw.sbb.checkin.domain.tour.service.VisitorTrendService;
import idusw.sbb.checkin.domain.tour.service.VisitorTrendService.Status;
import idusw.sbb.checkin.domain.tour.service.VisitorTrendService.Trend;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 집중률이 오지 않으면 방문자 수로 가늠하되, 추정이라는 것을 source 로 갈라 준다.
 * 방문자 자료도 없으면 지어내지 않고 값 없음으로 둔다.
 */
class CrowdVisitorFallbackTest {

    private final ConcentrationService conc = mock(ConcentrationService.class);
    private final VisitorTrendService visitor = mock(VisitorTrendService.class);
    private final CrowdService service = new CrowdService(conc, visitor);
    private final LocalDate day = LocalDate.of(2026, 9, 21);

    @Test
    void 집중률이_없으면_방문자_추정이_나오고_source_가_다르다() {
        AreaCode.Area yeosu = AreaCode.find("여수시");
        when(visitor.summary(any(AreaCode.Area.class))).thenReturn(
                new Trend(Status.OK, "전남", "20260914", "일", 120_000L, 100_000L, 20, ""));

        CrowdForecast f = service.forecast(yeosu.areaCd(), yeosu.signguCd(), "오동도", day);

        assertThat(f.source()).isEqualTo("VISITOR_EST");
        assertThat(f.rate()).as("집중률 눈금이 아니므로 숫자를 붙이지 않는다").isNull();
        assertThat(f.levelKey()).isEqualTo("high");
        assertThat(f.note()).startsWith("추정").contains("20%");
        verifyNoInteractions(conc);
    }

    @Test
    void 집중률이_오면_그대로_쓰고_방문자는_묻지_않는다() {
        AreaCode.Area hd = AreaCode.find("해운대구");
        when(conc.predict(anyString(), anyString(), anyInt(), anyInt())).thenReturn(List.of(
                new ConcentrationRate("20260921", "해운대해수욕장", "부산", "해운대구", 90.0)));

        CrowdForecast f = service.forecast(hd.areaCd(), hd.signguCd(), "해운대 해수욕장", day);

        assertThat(f.source()).isEqualTo("TOUR");
        assertThat(f.rate()).isEqualTo(90.0);
        verifyNoInteractions(visitor);
    }

    @Test
    void 방문자_자료도_없으면_값_없음이다() {
        AreaCode.Area yeosu = AreaCode.find("여수시");
        when(visitor.summary(any(AreaCode.Area.class))).thenReturn(
                new Trend(Status.NO_DATA, "전남", null, null, null, null, null, ""));

        CrowdForecast f = service.forecast(yeosu.areaCd(), yeosu.signguCd(), "오동도", day);

        assertThat(f.source()).isEqualTo("NONE");
        assertThat(f.levelKey()).isNull();
    }
}
