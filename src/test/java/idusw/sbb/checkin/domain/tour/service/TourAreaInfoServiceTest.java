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
                java.util.Map.of("name", "경주 오토캠핑장", "addr", "경북 경주시", "lat", 35.8, "lon", 129.2)));
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
        assertThat(r.camping().items().get(0).name()).isEqualTo("경주 오토캠핑장");
        assertThat(r.trails().status()).isEqualTo(Status.NONE);   // 받았는데 0건 — 「확인 안 됨」이 아니다

        /* 다른 시도 줄(서울)이 섞여 들어오면 안 된다. 경북 외지인 평균은 (30+50)/2 = 40만 */
        assertThat(r.visitors().status()).isEqualTo(Status.OK);
        assertThat(r.visitors().items().get(0).outsidersPerDay()).isEqualTo(400_000L);
        assertThat(r.visitors().items().get(0).localsPerDay()).isEqualTo(900_000L);
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

    @Test
    void 좌표가_다른_시도면_여행지를_따른다() {
        when(origin.regionAt(37.5, 127.0)).thenReturn("서울 강남구");
        assertThat(svc.resolveArea("경주", 37.5, 127.0).fullName()).isEqualTo("경북 경주시");
        when(origin.regionAt(35.16, 129.16)).thenReturn("부산 해운대구");
        assertThat(svc.resolveArea("부산", 35.16, 129.16).fullName()).isEqualTo("부산 해운대구");
    }
}
