package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.ConcentrationRate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TatsCnctrRateService 관광지 집중률 예측 — 붐빔 판정·시간대별 막대 (홍성찬·유지환).
 * 한 관광지에 대해 여러 날짜(baseYmd)가 배열로 온다.
 */
@Service
@RequiredArgsConstructor
public class ConcentrationService {

    private static final String SERVICE = "TatsCnctrRateService";

    private final TourApiClient client;

    /** 특정 시군구 관광지들의 날짜별 집중률 예측 (tatsCnctrRatedList). */
    public List<ConcentrationRate> predict(String areaCd, String signguCd, int numOfRows, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("areaCd", areaCd);
        params.put("signguCd", signguCd);

        JsonNode items = client.items(SERVICE, "tatsCnctrRatedList", params);
        List<ConcentrationRate> result = new ArrayList<>();
        if (items.isObject()) {
            result.add(toRate(items));
        } else {
            for (JsonNode item : items) result.add(toRate(item));
        }
        return result;
    }

    private ConcentrationRate toRate(JsonNode n) {
        return new ConcentrationRate(
                text(n, "baseYmd"),
                text(n, "tAtsNm"),
                text(n, "areaNm"),
                text(n, "signguNm"),
                parseDouble(text(n, "cnctrRate"))
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
