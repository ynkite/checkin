package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.service.TourAreaInfoService.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TourAreaInfoServiceTest {

    private final ObjectMapper om = new ObjectMapper();
    private final TourApiClient client = mock(TourApiClient.class);
    private final LocgoHubService hubs = mock(LocgoHubService.class);
    private final OriginSearchService origin = mock(OriginSearchService.class);
    private final TourExtraService extra = mock(TourExtraService.class);
    private final VisitorService visitors = mock(VisitorService.class);
    private final TourAreaInfoService svc = new TourAreaInfoService(client, hubs, origin, extra, visitors);

    private void area(String json) throws Exception {
        when(client.tryItems(eq("KorService2"), eq("areaCode2"), anyMap())).thenReturn(Optional.of(om.readTree(json)));
    }

    @Test
    void 반려동물_목록을_못_받으면_UNAVAILABLE_받았는데_0건이면_NONE() throws Exception {
        area("[{\"code\":\"2\",\"name\":\"경주시\"}]");
        when(hubs.hubList(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(client.tryItems(eq("KorPetTourService2"), eq("areaBasedList2"), anyMap())).thenReturn(Optional.empty());
        when(client.tryItems(eq("KorWithService2"), eq("areaBasedList2"), anyMap())).thenReturn(Optional.of(om.createArrayNode()));

        TourAreaInfoService.AreaInfo r = svc.lookup("경주", null, null);
        assertThat(r.areaName()).isEqualTo("경북 경주시");
        assertThat(r.pet().status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(r.barrierFree().status()).isEqualTo(Status.NONE);
        assertThat(r.hubs().status()).isEqualTo(Status.UNAVAILABLE);   // 거점은 못 받으면 화면이 줄을 감춘다
        verify(client).tryItems(eq("KorWithService2"), eq("areaBasedList2"),
                argThat(p -> "35".equals(p.get("areaCode")) && "2".equals(p.get("sigunguCode"))));
    }

    @Test
    void 무장애_상세를_못_받은_곳은_facts_가_null_이고_빈칸_항목은_빼고_받은_문구를_그대로_둔다() throws Exception {
        area("[{\"code\":\"2\",\"name\":\"경주시\"}]");
        when(hubs.hubList(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(client.tryItems(eq("KorPetTourService2"), anyString(), anyMap())).thenReturn(Optional.of(om.createArrayNode()));
        when(client.tryItems(eq("KorWithService2"), eq("areaBasedList2"), anyMap())).thenReturn(Optional.of(om.readTree(
                "[{\"contentid\":\"1\",\"title\":\"감포항\",\"addr1\":\"경주시\",\"mapx\":\"129.5\",\"mapy\":\"35.8\"}," +
                " {\"contentid\":\"2\",\"title\":\"강동 워터파크\",\"addr1\":\"경주시\"}]")));
        when(client.tryItems(eq("KorWithService2"), eq("detailWithTour2"), argThat(p -> p != null && "1".equals(p.get("contentId")))))
                .thenReturn(Optional.of(om.readTree("[{\"restroom\":\"장애인 화장실 있음\",\"elevator\":\"\",\"route\":\"턱이 없음\"}]")));
        when(client.tryItems(eq("KorWithService2"), eq("detailWithTour2"), argThat(p -> p != null && "2".equals(p.get("contentId")))))
                .thenReturn(Optional.empty());

        TourAreaInfoService.AreaInfo r = svc.lookup("경주", null, null);
        assertThat(r.barrierFree().status()).isEqualTo(Status.OK);
        assertThat(r.barrierFree().count()).isEqualTo(2);
        var first = r.barrierFree().items().get(0);
        assertThat(first.facts()).extracting(TourAreaInfoService.Fact::label).containsExactly("접근로", "화장실");
        assertThat(first.facts().get(1).text()).isEqualTo("장애인 화장실 있음");
        assertThat(first.lat()).isEqualTo(35.8);
        assertThat(r.barrierFree().items().get(1).facts()).isNull();   // 「없음」이 아니라 「확인되지 않음」
    }

    @Test
    void 시군구_이름이_구까지_있으면_시_이름으로_찾고_실패는_담아_두지_않는다() throws Exception {
        area("[{\"code\":\"13\",\"name\":\"수원시\"}]");
        assertThat(svc.tourSigunguCode("31", "수원시 팔달구")).isEqualTo("13");

        TourAreaInfoService fresh = new TourAreaInfoService(client, hubs, origin, extra, visitors);
        when(client.tryItems(eq("KorService2"), eq("areaCode2"), anyMap())).thenReturn(Optional.empty());
        assertThat(fresh.tourSigunguCode("35", "경주시")).isNull();
        assertThat(fresh.tourSigunguCode("35", "경주시")).isNull();
        verify(client, atLeast(2)).tryItems(eq("KorService2"), eq("areaCode2"), anyMap());
    }

    @Test
    void 모르는_여행지는_부르지_않는다() {
        TourAreaInfoService.AreaInfo r = svc.lookup("아틀란티스", null, null);
        assertThat(r.areaName()).isNull();
        assertThat(r.pet().status()).isEqualTo(Status.NO_AREA);
        verifyNoInteractions(client, hubs, extra, visitors);
    }

    /* 이 판이 고캠핑·두루누비·데이터랩의 유일한 소비처다. 부르지 않으면 심사에서
       호출건수 0 으로 대조에 걸린다. 「부르는가」를 못으로 박아 둔다. */
    @Test
    void 야영장_둘레길_방문자수를_실제로_부른다() throws Exception {
        area("[{\"code\":\"2\",\"name\":\"경주시\"}]");
        when(hubs.hubList(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(client.tryItems(anyString(), eq("areaBasedList2"), anyMap())).thenReturn(Optional.empty());
        when(extra.camping(eq("35"), anyInt())).thenReturn(List.of(
                java.util.Map.of("name", "경주 오토캠핑장", "address", "경북 경주시 산내면", "lat", 35.8, "lon", 129.2),
                java.util.Map.of("name", "포항 해변캠핑장", "address", "경북 포항시 북구", "lat", 36.0, "lon", 129.4)));
        when(extra.trails(eq("35"), anyInt())).thenReturn(List.of());
        when(visitors.daily(anyString(), anyString(), anyInt(), anyInt())).thenReturn(List.of(
                new idusw.sbb.checkin.domain.tour.dto.VisitorCount("20260620", "35", "경상북도", "평일", "외지인(b)", 300000),
                new idusw.sbb.checkin.domain.tour.dto.VisitorCount("20260621", "35", "경상북도", "주말", "외지인(b)", 500000),
                new idusw.sbb.checkin.domain.tour.dto.VisitorCount("20260620", "35", "경상북도", "평일", "현지인(a)", 900000),
                new idusw.sbb.checkin.domain.tour.dto.VisitorCount("20260620", "11", "서울특별시", "평일", "외지인(b)", 9000000)));

        TourAreaInfoService.AreaInfo r = svc.lookup("경주", null, null);

        verify(extra).camping(eq("35"), anyInt());
        verify(extra).trails(eq("35"), anyInt());
        verify(visitors).daily(anyString(), anyString(), anyInt(), anyInt());

        assertThat(r.camping().status()).isEqualTo(Status.OK);
        /* 시도(경북)로만 걸러져 포항도 섞여 온다. 여행지가 경주면 경주만 남긴다 —
           여수 화면에 담양 야영장이 올라오던 것과 같은 문제다 */
        assertThat(r.camping().items()).extracting(TourAreaInfoService.Spot::name)
                .containsExactly("경주 오토캠핑장");
        assertThat(r.camping().note()).isNull();   // 좁혀서 남았으니 변명할 것이 없다
        assertThat(r.trails().status()).isEqualTo(Status.NONE);   // 받았는데 0건 — 「확인 안 됨」이 아니다

        /* 다른 시도 줄(서울)이 섞여 들어오면 안 된다. 경북 외지인 평균은 (30+50)/2 = 40만 */
        assertThat(r.visitors().status()).isEqualTo(Status.OK);
        assertThat(r.visitors().items().get(0).outsidersPerDay()).isEqualTo(400_000L);
        assertThat(r.visitors().items().get(0).localsPerDay()).isEqualTo(900_000L);
    }

    @Test
    void 시군구에_하나도_없으면_시도로_넓히되_그렇다고_말한다() throws Exception {
        area("[{\"code\":\"2\",\"name\":\"경주시\"}]");
        when(hubs.hubList(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(client.tryItems(anyString(), eq("areaBasedList2"), anyMap())).thenReturn(Optional.empty());
        when(extra.camping(eq("35"), anyInt())).thenReturn(List.of(
                java.util.Map.of("name", "포항 해변캠핑장", "address", "경북 포항시 북구")));

        var sec = svc.lookup("경주", null, null).camping();
        assertThat(sec.status()).isEqualTo(Status.OK);
        assertThat(sec.items()).hasSize(1);
        /* 없는 척도, 경주 것인 척도 하지 않는다 */
        assertThat(sec.note()).contains("경주시").contains("시도");
    }

    @Test
    void 방문자수를_못_받으면_없다고_하지_않는다() throws Exception {
        area("[{\"code\":\"2\",\"name\":\"경주시\"}]");
        when(hubs.hubList(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(client.tryItems(anyString(), eq("areaBasedList2"), anyMap())).thenReturn(Optional.empty());
        when(visitors.daily(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("관광공사 응답 없음"));

        assertThat(svc.lookup("경주", null, null).visitors().status()).isEqualTo(Status.UNAVAILABLE);
    }

    /* 두루누비는 주소 대신 코스 설명을 준다. <br> 태그가 섞인 서너 문단이다.
       그대로 두면 화면이 밀리고 태그가 글자로 보인다. 실호출로 확인한 모양이다. */
    @Test
    void 주소_칸에_글_덩어리가_오면_한_줄로_줄인다() {
        assertThat(TourAreaInfoService.oneLine("부산광역시 기장군 장안읍 1-5번지"))
                .isEqualTo("부산광역시 기장군 장안읍 1-5번지");

        String 코스 = "- 부산의 남단 오륙도에서 시작하여 광안대교와 해운대해수욕장, "
                + "미포항까지 이어지는 코스<br>- 해안절경을 감상할 수 있는 구간";
        String 줄인것 = TourAreaInfoService.oneLine(코스);
        assertThat(줄인것).doesNotContain("<br>").doesNotContain("<");
        assertThat(줄인것).doesNotStartWith("-");
        assertThat(줄인것).endsWith("…");
        assertThat(줄인것.length()).isLessThanOrEqualTo(47);

        assertThat(TourAreaInfoService.oneLine(null)).isNull();
        assertThat(TourAreaInfoService.oneLine("   ")).isNull();
        assertThat(TourAreaInfoService.oneLine("<br><br>")).isNull();
    }

    @Test
    void 좌표가_다른_시도면_여행지를_따른다() {
        when(origin.regionAt(37.5, 127.0)).thenReturn("서울 강남구");
        assertThat(svc.resolveArea("경주", 37.5, 127.0).fullName()).isEqualTo("경북 경주시");
        when(origin.regionAt(35.16, 129.16)).thenReturn("부산 해운대구");
        assertThat(svc.resolveArea("부산", 35.16, 129.16).fullName()).isEqualTo("부산 해운대구");
    }
}
