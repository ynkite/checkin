package idusw.sbb.checkin.domain.tour;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.service.TourExtraService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 고캠핑·두루누비는 서버 지역 파라미터가 없어 전국을 받아 응답 지역 필드로 거른다.
 * 시도명이 풀네임(경상남도)·약칭(경남) 섞여 나오는 걸 조각 매칭으로 거르는지 본다.
 */
class TourExtraRegionTest {

    private final ObjectMapper om = new ObjectMapper();

    private TourExtraService serviceReturning(String itemsJson) {
        TourApiClient client = mock(TourApiClient.class);
        try { when(client.items(any(), any(), any())).thenReturn(om.readTree(itemsJson)); }
        catch (Exception e) { throw new RuntimeException(e); }
        return new TourExtraService(client);
    }

    @Test
    void 고캠핑_부산만_거른다() {
        // 전국 응답 — 부산·경남·서울 섞임 (doNm 은 풀네임)
        String json = """
            [{"facltNm":"부산캠핑장","doNm":"부산광역시","sigunguNm":"강서구"},
             {"facltNm":"경남캠핑장","doNm":"경상남도","sigunguNm":"창원시"},
             {"facltNm":"서울캠핑장","doNm":"서울특별시","sigunguNm":"강동구"}]
            """;
        TourExtraService svc = serviceReturning(json);

        List<Map<String, Object>> busan = svc.camping("6", 10);   // 부산
        assertEquals(1, busan.size());
        assertEquals("부산캠핑장", busan.get(0).get("name"));
        assertFalse(busan.get(0).containsKey("_region"), "내부 필드는 화면에 안 나간다");
    }

    @Test
    void 고캠핑_경남은_풀네임_경상남도를_약칭코드로_찾는다() {
        String json = """
            [{"facltNm":"부산캠핑장","doNm":"부산광역시"},
             {"facltNm":"경남캠핑장","doNm":"경상남도"}]
            """;
        // areaCode 36(경남) 은 조각 {"경상남","경남"} 으로 "경상남도" 를 잡아야 한다
        List<Map<String, Object>> gn = serviceReturning(json).camping("36", 10);
        assertEquals(1, gn.size());
        assertEquals("경남캠핑장", gn.get(0).get("name"));
    }

    @Test
    void 두루누비_sigun_약칭으로_거른다() {
        String json = """
            [{"crsKorNm":"남파랑길 3코스","sigun":"부산 영도구"},
             {"crsKorNm":"경남 코스","sigun":"경남 창원시"}]
            """;
        List<Map<String, Object>> busan = serviceReturning(json).trails("6", 10);
        assertEquals(1, busan.size());
        assertEquals("남파랑길 3코스", busan.get(0).get("name"));
    }
}
