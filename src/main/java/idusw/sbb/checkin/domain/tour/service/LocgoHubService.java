package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.HubSpot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LocgoHubTarService1 기초지자체 중심 관광지 — 동선 클러스터의 허브 선정 (홍성찬).
 * 좌표 필드가 mapX/mapY(대문자)임에 주의.
 */
@Service
@RequiredArgsConstructor
public class LocgoHubService {

    private static final String SERVICE = "LocgoHubTarService1";

    private final TourApiClient client;

    /** 특정 시군구의 중심 관광지 목록 (areaBasedList1). baseYm/areaCd/signguCd 필수. */
    public List<HubSpot> hubList(String baseYm, String areaCd, String signguCd, int numOfRows, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("baseYm", baseYm);
        params.put("areaCd", areaCd);
        params.put("signguCd", signguCd);

        JsonNode items = client.items(SERVICE, "areaBasedList1", params);
        List<HubSpot> result = new ArrayList<>();
        if (items.isObject()) {
            result.add(toHub(items));
        } else {
            for (JsonNode item : items) result.add(toHub(item));
        }
        return result;
    }

    private HubSpot toHub(JsonNode n) {
        return new HubSpot(
                text(n, "hubTatsNm"),
                parseDouble(text(n, "mapY")),    // 위도
                parseDouble(text(n, "mapX")),    // 경도
                text(n, "areaCd"),
                text(n, "signguCd"),
                text(n, "hubCtgryLclsNm"),
                parseInt(text(n, "hubRank"))
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

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }
}
