package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.RelatedSpot;
import idusw.sbb.checkin.domain.tour.service.RelatedTourService.Status;
import idusw.sbb.checkin.domain.tour.service.RelatedTourService.Suggestion;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 챗봇이 쓰는 연관 관광지 추천. 행 모양은 2026-08 경주시(47130) 실제 응답에서 옮겼다. */
class RelatedTourSuggestTest {

    private static final YearMonth SEP_2026 = YearMonth.of(2026, 9);
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode rows() throws Exception {
        return om.readTree("""
            [
             {"tAtsNm":"불국사","rlteTatsNm":"국립경주박물관","rlteCtgryLclsNm":"관광지","rlteRank":"1","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"불국사","rlteTatsNm":"첨성대","rlteCtgryLclsNm":"관광지","rlteRank":"2","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"불국사","rlteTatsNm":"동궁과월지","rlteCtgryLclsNm":"관광지","rlteRank":"3","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"불국사","rlteTatsNm":"황리단길","rlteCtgryLclsNm":"음식","rlteRank":"4","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"불국사","rlteTatsNm":"라한셀렉트/경주","rlteCtgryLclsNm":"숙박","rlteRank":"6","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"감포항","rlteTatsNm":"문무대왕릉","rlteCtgryLclsNm":"관광지","rlteRank":"1","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"감포항","rlteTatsNm":"첨성대","rlteCtgryLclsNm":"관광지","rlteRank":"2","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"경주역","rlteTatsNm":"첨성대","rlteCtgryLclsNm":"관광지","rlteRank":"1","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"},
             {"tAtsNm":"경주역","rlteTatsNm":"문무대왕릉","rlteCtgryLclsNm":"관광지","rlteRank":"2","rlteRegnNm":"경상북도","rlteSignguNm":"경주시"}
            ]
            """);
    }

    private TourApiClient client(JsonNode items) {
        TourApiClient c = mock(TourApiClient.class);
        when(c.items(any(), any(), anyMap())).thenReturn(items);
        return c;
    }

    @Test
    void 일정_장소가_기준_관광지이면_함께_간_곳을_순위대로_주고_일정에_있는_곳과_숙박은_뺀다() throws Exception {
        TourApiClient c = client(rows());
        Suggestion s = new RelatedTourService(c).suggest("경주", List.of("경주 불국사"), List.of("첨성대"), 5, SEP_2026);

        assertThat(s.status()).isEqualTo(Status.OK);
        assertThat(s.basedOn()).isEqualTo("경주 불국사");
        assertThat(s.baseYm()).isEqualTo("202608");       // 이번 달이 아니라 직전 달
        assertThat(s.spots()).extracting(RelatedSpot::toName)
                .containsExactly("국립경주박물관", "동궁과월지", "황리단길");
        verify(c).items(eq("TarRlteTarService1"), eq("areaBasedList1"),
                argThat((Map<String, String> p) -> "47130".equals(p.get("signguCd")) && "47".equals(p.get("areaCd"))));
    }

    @Test
    void 맞는_장소가_없으면_지역에서_가장_자주_묶인_곳을_준다() throws Exception {
        Suggestion s = new RelatedTourService(client(rows())).suggest("경주시", List.of("없는카페"), List.of(), 2, SEP_2026);

        assertThat(s.status()).isEqualTo(Status.OK);
        assertThat(s.basedOn()).isNull();
        assertThat(s.spots()).extracting(RelatedSpot::toName).containsExactly("첨성대", "문무대왕릉");
    }

    @Test
    void 지역코드를_모르는_여행지는_부르지_않고_NO_AREA() {
        TourApiClient c = mock(TourApiClient.class);
        Suggestion s = new RelatedTourService(c).suggest("아틀란티스", List.of(), List.of(), 5, SEP_2026);

        assertThat(s.status()).isEqualTo(Status.NO_AREA);
        verifyNoInteractions(c);
    }

    @Test
    void 못_받으면_전달까지_묻고_UNAVAILABLE_이며_빈_결과는_캐시하지_않는다() {
        TourApiClient c = client(om.createArrayNode());
        RelatedTourService svc = new RelatedTourService(c);

        Suggestion s = svc.suggest("경주", List.of("불국사"), List.of(), 5, SEP_2026);
        assertThat(s.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(s.spots()).isEmpty();
        verify(c).items(any(), any(), argThat((Map<String, String> p) -> "202608".equals(p.get("baseYm"))));
        verify(c).items(any(), any(), argThat((Map<String, String> p) -> "202607".equals(p.get("baseYm"))));

        svc.suggest("경주", List.of("불국사"), List.of(), 5, SEP_2026);
        verify(c, times(4)).items(any(), any(), anyMap());   // 실패는 담지 않았으니 다시 묻는다
    }

    @Test
    void 받은_목록은_캐시해서_대화마다_다시_부르지_않는다() throws Exception {
        TourApiClient c = client(rows());
        RelatedTourService svc = new RelatedTourService(c);
        svc.suggest("경주", List.of("불국사"), List.of(), 5, SEP_2026);
        svc.suggest("경주", List.of("감포항"), List.of(), 5, SEP_2026);
        verify(c, times(1)).items(any(), any(), anyMap());
    }

    @Test
    void 이름_맞추기는_지역명_정도의_차이만_받는다() {
        assertThat(RelatedTourService.sameName("불국사", "경주불국사")).isTrue();
        assertThat(RelatedTourService.sameName("경주역", "경주역사유적지구")).isFalse();
        assertThat(RelatedTourService.sameName("첨성대", "첨성")).isFalse();
        assertThat(RelatedTourService.sameName("", "불국사")).isFalse();
    }
}
