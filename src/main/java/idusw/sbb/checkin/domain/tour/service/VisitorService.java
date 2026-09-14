package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.VisitorCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataLabService 지역별 방문자 수 — 집중률의 평소 기준선(실측).
 * 시도 단위 일별, 현지인(a)/외지인(b) 행으로 나뉜다.
 */
@Service
@RequiredArgsConstructor
public class VisitorService {

    private static final String SERVICE = "DataLabService";

    private final TourApiClient client;

    /** 기간 내 시도별 일별 방문자 수 (metcoRegnVisitrDDList). startYmd/endYmd 필수(YYYYMMDD). */
    public List<VisitorCount> daily(String startYmd, String endYmd, int numOfRows, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("startYmd", startYmd);
        params.put("endYmd", endYmd);

        JsonNode items = client.items(SERVICE, "metcoRegnVisitrDDList", params);
        List<VisitorCount> result = new ArrayList<>();
        if (items.isObject()) {
            result.add(toCount(items));
        } else {
            for (JsonNode item : items) result.add(toCount(item));
        }
        return result;
    }

    private VisitorCount toCount(JsonNode n) {
        return new VisitorCount(
                text(n, "baseYmd"),
                text(n, "areaCode"),
                text(n, "areaNm"),
                text(n, "daywkDivNm"),
                text(n, "touDivNm"),
                parseDouble(text(n, "touNum"))
        );
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() ? "" : v.asText();
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return 0.0; }
    }
}
