package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 받아 놓고 안 쓰던 관광공사 네 가지를 붙인다.
 *
 *   KorWithService2     무장애 여행   — 장애인·어르신 동반
 *   KorPetTourService2  반려동물 동반 — 화면에 칩은 이미 있었다
 *   GoCamping           고캠핑        — 백패킹·차박
 *   Durunubi            두루누비      — 도보·자전거 코스
 *
 * 왜 필요한가 — 플래너의 「따로 챙길 것」에 반려동물·노인 동반 칩이
 * 이미 있는데, 고른 값이 장소 고르는 데 아무 영향을 안 줬다.
 * 칩만 있고 데이터가 없으면 물어본 의미가 없다.
 *
 * 응답 모양이 넷 다 달라서 공통 DTO 로 억지로 묶지 않는다.
 * 필요한 것만 뽑아 Map 으로 낸다 — 화면이 쓰는 건 이름·좌표·주소뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourExtraService {

    private final TourApiClient client;

    /** 무장애 여행 — 휠체어·유아차가 다닐 수 있는 곳 */
    public List<Map<String, Object>> barrierFree(String areaCode, int rows) {
        return simple("KorWithService2", "areaBasedList1",
                base(areaCode, rows), "무장애");
    }

    /** 반려동물 동반 가능한 곳 */
    public List<Map<String, Object>> petFriendly(String areaCode, int rows) {
        return simple("KorPetTourService2", "areaBasedList",
                base(areaCode, rows), "반려동물");
    }

    /** 고캠핑 — 야영장. 백패킹·차박 여행 */
    public List<Map<String, Object>> camping(String areaCode, int rows) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", String.valueOf(rows));
        p.put("pageNo", "1");
        if (areaCode != null && !areaCode.isBlank()) p.put("doNm", areaCode);
        return simple("GoCamping", "basedList", p, "야영장");
    }

    /** 두루누비 — 걷는 길·자전거 길 코스 */
    public List<Map<String, Object>> trails(String areaCode, int rows) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", String.valueOf(rows));
        p.put("pageNo", "1");
        if (areaCode != null && !areaCode.isBlank()) p.put("brdDiv", areaCode);
        return simple("Durunubi", "courseList", p, "코스");
    }

    /* ── 바닥 ──────────────────────────────────────────────── */

    private Map<String, String> base(String areaCode, int rows) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", String.valueOf(rows));
        p.put("pageNo", "1");
        p.put("arrange", "A");
        if (areaCode != null && !areaCode.isBlank()) p.put("areaCode", areaCode);
        return p;
    }

    /**
     * 넷의 응답 필드 이름이 제각각이다. 흔히 쓰는 이름을 순서대로 찾아
     * 있는 것을 쓴다. 없으면 그 항목을 건너뛴다 — 이름이 없는 장소는
     * 화면에 못 올린다.
     */
    private List<Map<String, Object>> simple(String service, String op,
                                             Map<String, String> params, String kind) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode items = client.items(service, op, params);
            if (items == null) return out;
            for (JsonNode it : items.isArray() ? items : List.of(items)) {
                String name = first(it, "title", "facltNm", "crsKorNm", "mainTitle");
                if (name == null || name.isBlank()) continue;

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", name);
                m.put("kind", kind);
                m.put("address", first(it, "addr1", "addr", "sigunguNm", "crsSummary"));
                Double lat = num(it, "mapy", "mapY", "gpsY", "latitude");
                Double lon = num(it, "mapx", "mapX", "gpsX", "longitude");
                if (lat != null && lon != null) { m.put("lat", lat); m.put("lon", lon); }
                m.put("imageUrl", first(it, "firstimage", "firstImageUrl", "imgFile"));
                out.add(m);
            }
        } catch (Exception e) {
            log.warn("[tour] {} 실패: {}", service, e.getMessage());
        }
        return out;
    }

    private static String first(JsonNode n, String... keys) {
        for (String k : keys) {
            String v = n.path(k).asText("");
            if (!v.isBlank()) return v;
        }
        return null;
    }

    private static Double num(JsonNode n, String... keys) {
        for (String k : keys) {
            String v = n.path(k).asText("");
            if (v.isBlank()) continue;
            try { return Double.parseDouble(v); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    /** 자체 점검 — 필드 이름 찾기가 맞는지. API 를 부르지 않는다. */
    public static void main(String[] args) throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        JsonNode a = om.readTree("{\"title\":\"해운대해수욕장\",\"mapy\":\"35.15\",\"mapx\":\"129.16\"}");
        JsonNode b = om.readTree("{\"facltNm\":\"달빛야영장\",\"gpsY\":\"36.1\",\"gpsX\":\"127.2\"}");
        JsonNode c = om.readTree("{\"crsKorNm\":\"해파랑길 1코스\"}");
        assert "해운대해수욕장".equals(first(a, "title", "facltNm", "crsKorNm"));
        assert "달빛야영장".equals(first(b, "title", "facltNm", "crsKorNm"));
        assert "해파랑길 1코스".equals(first(c, "title", "facltNm", "crsKorNm"));
        assert Math.abs(num(a, "mapy", "gpsY") - 35.15) < 1e-9;
        assert Math.abs(num(b, "mapy", "gpsY") - 36.1) < 1e-9;
        assert num(c, "mapy", "gpsY") == null;
        System.out.println("TourExtraService 자체 점검 통과");
    }
}
