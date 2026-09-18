package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.budget.dto.FestivalLookup;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 축제 조회의 「확인 안 됨」/「없음」/「있음」 구분 검증.
 * 관광공사 호출은 목킹 — searchFestival2 응답을 흉내낸다.
 */
class FestivalLookupTest {

    private final ObjectMapper om = new ObjectMapper();
    private final LocalDate from = LocalDate.of(2026, 8, 1);
    private final LocalDate to   = LocalDate.of(2026, 8, 3);

    private FestivalService serviceWith(Optional<String> itemsJson) {
        TourApiClient client = mock(TourApiClient.class);
        try {
            when(client.tryItems(any(), any(), any())).thenReturn(
                    itemsJson.isEmpty() ? Optional.empty() : Optional.of(om.readTree(itemsJson.get())));
        } catch (Exception e) { throw new RuntimeException(e); }
        return new FestivalService(client);
    }

    @Test
    void 호출_실패면_UNKNOWN() {
        FestivalService s = serviceWith(Optional.empty());   // tryItems 가 실패 신호
        assertEquals(FestivalLookup.Status.UNKNOWN, s.lookup("6", from, to).status());
    }

    @Test
    void 응답은_왔지만_0건이면_NONE() {
        FestivalService s = serviceWith(Optional.of("[]"));  // 0000, 빈 목록
        assertEquals(FestivalLookup.Status.NONE, s.lookup("6", from, to).status());
    }

    @Test
    void 기간에_겹치는_축제가_있으면_FOUND() {
        String json = """
            [{"contentid":"1","title":"부산바다축제","eventstartdate":"20260730","eventenddate":"20260805",
              "addr1":"부산","addr2":"","firstimage":"","mapy":"35.1","mapx":"129.1"}]
            """;
        FestivalService s = serviceWith(Optional.of(json));
        FestivalLookup r = s.lookup("6", from, to);
        assertEquals(FestivalLookup.Status.FOUND, r.status());
        assertEquals(1, r.festivals().size());
        assertEquals("부산바다축제", r.festivals().get(0).title());
    }

    @Test
    void 기간에_안겹치는_축제뿐이면_NONE() {
        String json = """
            [{"contentid":"2","title":"겨울축제","eventstartdate":"20261201","eventenddate":"20261225",
              "addr1":"부산","addr2":"","firstimage":"","mapy":"35.1","mapx":"129.1"}]
            """;
        FestivalService s = serviceWith(Optional.of(json));
        assertEquals(FestivalLookup.Status.NONE, s.lookup("6", from, to).status());
    }
}
