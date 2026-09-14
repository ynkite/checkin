package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.RelatedSpot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TarRlteTarService1 관광지별 연관 관광지 — 대안 후보 선별 (홍성찬 동선 엔진).
 * rlteCtgryLclsNm(관광지/음식/숙박) 로 유형이 나뉘고 rlteRank 로 순위가 온다.
 */
@Service
@RequiredArgsConstructor
public class RelatedTourService {

    private static final String SERVICE = "TarRlteTarService1";

    private final TourApiClient client;

    /**
     * 특정 시군구의 연관 관광지 목록 (areaBasedList1).
     *
     * @param baseYm    기준 연월 YYYYMM (필수)
     * @param areaCd    지역코드 (필수)
     * @param signguCd  시군구코드 (필수)
     */
    public List<RelatedSpot> relatedList(String baseYm, String areaCd, String signguCd, int numOfRows, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("baseYm", baseYm);
        params.put("areaCd", areaCd);
        params.put("signguCd", signguCd);

        JsonNode items = client.items(SERVICE, "areaBasedList1", params);
        List<RelatedSpot> result = new ArrayList<>();
        if (items.isObject()) {
            result.add(toRelated(items));
        } else {
            for (JsonNode item : items) result.add(toRelated(item));
        }
        return result;
    }

    private RelatedSpot toRelated(JsonNode n) {
        return new RelatedSpot(
                text(n, "tAtsNm"),
                text(n, "rlteTatsNm"),
                text(n, "rlteCtgryLclsNm"),
                parseInt(text(n, "rlteRank")),
                text(n, "rlteRegnNm"),
                text(n, "rlteSignguNm")
        );
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() ? "" : v.asText();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }
}
