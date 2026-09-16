package idusw.sbb.checkin.domain.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.*;
import idusw.sbb.checkin.domain.tour.service.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 5종 서비스의 정규화(필드 매핑)를 실제 응답 JSON 으로 검증한다.
 * Spring 컨텍스트를 띄우지 않으므로(OAuth 등 무관) 순수 단위 테스트.
 * 진짜 리스크 = 필드명 오타 (mapx vs mapX, areacode vs areaCd, firstimage 빈값 처리).
 */
class TourNormalizationTest {

    private final ObjectMapper om = new ObjectMapper();

    private TourApiClient clientReturning(String itemsJson) {
        try {
            JsonNode items = om.readTree(itemsJson);
            TourApiClient client = mock(TourApiClient.class);
            when(client.items(any(), any(), any())).thenReturn(items);
            return client;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void korService2_좌표와_빈이미지_정규화() {
        // firstimage 가 빈 문자열로 오는 실제 케이스 포함
        String json = """
            [{"contentid":"129156","title":"가덕도 등대","mapx":"128.8295937487","mapy":"35.0006471157",
              "areacode":"6","sigungucode":"1","contenttypeid":"12","firstimage":"","addr1":"부산광역시 강서구 외양포로 10"}]
            """;
        List<Spot> spots = new KorTourService(clientReturning(json)).areaBasedList("6", null, 1, 1);

        assertEquals(1, spots.size());
        Spot s = spots.get(0);
        assertEquals("129156", s.id());
        assertEquals("가덕도 등대", s.name());
        assertEquals(35.0006471157, s.lat(), 1e-9);   // mapy → lat
        assertEquals(128.8295937487, s.lon(), 1e-9);  // mapx → lon
        assertEquals(12, s.contentTypeId());
        assertNull(s.imageUrl(), "firstimage 빈 문자열은 null 로 정규화");
    }

    @Test
    void locgoHub_대문자_좌표필드_정규화() {
        String json = """
            [{"mapX":"126.976899873351","mapY":"37.578072561267","areaCd":"11","signguCd":"11110",
              "hubTatsNm":"경복궁","hubCtgryLclsNm":"관광지","hubRank":"1"}]
            """;
        List<HubSpot> hubs = new LocgoHubService(clientReturning(json)).hubList("202506", "11", "11110", 1, 1);

        HubSpot h = hubs.get(0);
        assertEquals("경복궁", h.name());
        assertEquals(37.578072561267, h.lat(), 1e-9);   // mapY(대문자) → lat
        assertEquals(126.976899873351, h.lon(), 1e-9);  // mapX(대문자) → lon
        assertEquals(1, h.rank());
        assertEquals("관광지", h.category());
    }

    @Test
    void relatedTour_유형과_순위_정규화() {
        String json = """
            [{"tAtsNm":"가회민화박물관","rlteTatsNm":"1인1잔","rlteCtgryLclsNm":"음식",
              "rlteRank":"1","rlteRegnNm":"서울특별시","rlteSignguNm":"은평구"}]
            """;
        List<RelatedSpot> rel = new RelatedTourService(clientReturning(json)).relatedList("202506", "11", "11110", 1, 1);

        RelatedSpot r = rel.get(0);
        assertEquals("가회민화박물관", r.fromName());
        assertEquals("1인1잔", r.toName());
        assertEquals("음식", r.category());
        assertEquals(1, r.rank());
    }

    @Test
    void concentration_집중률_정규화() {
        String json = """
            [{"baseYmd":"20260910","areaNm":"서울특별시","signguNm":"종로구","tAtsNm":"가회민화박물관","cnctrRate":"82.78"}]
            """;
        List<ConcentrationRate> rates = new ConcentrationService(clientReturning(json)).predict("11", "11110", 1, 1);

        ConcentrationRate c = rates.get(0);
        assertEquals("20260910", c.date());
        assertEquals("가회민화박물관", c.placeName());
        assertEquals(82.78, c.rate(), 1e-9);
    }

    @Test
    void visitor_방문자수_정규화() {
        String json = """
            [{"areaCode":"11","areaNm":"서울특별시","daywkDivNm":"일요일","touDivNm":"현지인(a)","touNum":"4256122.5","baseYmd":"20250601"}]
            """;
        List<VisitorCount> counts = new VisitorService(clientReturning(json)).daily("20250601", "20250601", 1, 1);

        VisitorCount v = counts.get(0);
        assertEquals("11", v.areaCode());
        assertEquals("현지인(a)", v.visitorType());
        assertEquals(4256122.5, v.count(), 1e-6);
    }

    @Test
    void 결과_1건이면_배열아닌_객체로_와도_처리한다() {
        // 관광공사 API 는 결과가 1건이면 item 을 배열이 아닌 단일 객체로 준다
        String singleObject = """
            {"contentid":"1","title":"단일","mapx":"127.0","mapy":"37.0","contenttypeid":"12","firstimage":"http://x/y.jpg"}
            """;
        List<Spot> spots = new KorTourService(clientReturning(singleObject)).areaBasedList(null, null, 1, 1);

        assertEquals(1, spots.size());
        assertEquals("단일", spots.get(0).name());
        assertEquals("http://x/y.jpg", spots.get(0).imageUrl());
    }
}
