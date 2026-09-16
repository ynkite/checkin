package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.Spot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KorService2 국문 관광정보 — 관광지·숙소 기본정보, 대표 이미지, 좌표.
 * 화면마다 호출되므로 호출 이력을 쌓기에 가장 유리한 API (1순위).
 */
@Service
@RequiredArgsConstructor
public class KorTourService {

    private static final String SERVICE = "KorService2";

    private final TourApiClient client;

    /**
     * 지역 기반 관광정보 목록 (areaBasedList2).
     *
     * @param areaCode      지역코드 (null 이면 전국)
     * @param contentTypeId 관광타입 (12 관광지 / 32 숙박 등, null 이면 전체)
     * @param numOfRows     페이지당 개수
     * @param pageNo        페이지 번호
     */
    public List<Spot> areaBasedList(String areaCode, Integer contentTypeId, int numOfRows, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("arrange", "A");                      // A: 제목순 (이미지 유무 무관)
        if (areaCode != null)      params.put("areaCode", areaCode);
        if (contentTypeId != null) params.put("contentTypeId", String.valueOf(contentTypeId));

        return toSpots(client.items(SERVICE, "areaBasedList2", params));
    }

    private List<Spot> toSpots(JsonNode items) {
        List<Spot> result = new ArrayList<>();
        if (items.isObject()) {                          // 결과 1건이면 배열이 아닌 객체로 온다
            result.add(toSpot(items));
        } else {
            for (JsonNode item : items) result.add(toSpot(item));
        }
        return result;
    }

    private Spot toSpot(JsonNode n) {
        return new Spot(
                text(n, "contentid"),
                text(n, "title"),
                parseDouble(text(n, "mapy")),           // 위도
                parseDouble(text(n, "mapx")),           // 경도
                text(n, "areacode"),
                text(n, "sigungucode"),
                parseInteger(text(n, "contenttypeid")),
                blankToNull(text(n, "firstimage")),     // 빈 문자열 → null (빈 이미지 박스 방지)
                blankToNull(text(n, "addr1"))
        );
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() ? "" : v.asText();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return 0.0; }
    }

    private static Integer parseInteger(String s) {
        try { return Integer.valueOf(s.trim()); }
        catch (Exception e) { return null; }
    }
}
