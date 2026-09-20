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

    /* 오퍼레이션 이름은 areaBasedList2 다. areaBasedList·areaBasedList1 은
       「NO_OPENAPI_SERVICE_ERROR — 서비스가 없거나 폐기됨」이 온다 (2026-09-19 실호출로 확인) */

    /** 무장애 여행 — 휠체어·유아차가 다닐 수 있는 곳 */
    public List<Map<String, Object>> barrierFree(String areaCode, int rows) {
        return simple("KorWithService2", "areaBasedList2",
                base(areaCode, rows), "무장애");
    }

    /** 반려동물 동반 가능한 곳 */
    public List<Map<String, Object>> petFriendly(String areaCode, int rows) {
        return simple("KorPetTourService2", "areaBasedList2",
                base(areaCode, rows), "반려동물");
    }

    /* 고캠핑·두루누비는 지역으로 거르는 요청 파라미터가 없다 (2026-09-20 실호출 확인).
       - GoCamping doNm 은 INVALID_REQUEST_PARAMETER_ERROR — 존재하지 않는 파라미터
       - Durunubi brdDiv 는 코스 종류(DNWW 도보 / DNBK 자전거)지 지역이 아니다
       그래서 전국을 받아 응답의 지역 필드(doNm·sigun)로 우리가 거른다.
       전국 목록은 자주 안 바뀌므로 분 단위 메모리 캐시를 둔다 (영구 저장 아님). */

    private record Cached(List<Map<String, Object>> list, long at) {}
    private static final long TTL_MS = 10 * 60_000;
    private final Map<String, Cached> nationalCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 고캠핑 — 야영장. 지역 필터가 없어 전국을 받아 도명으로 거른다. */
    public List<Map<String, Object>> camping(String areaCode, int rows) {
        return filterByRegion(national("GoCamping", "basedList", "야영장", "doNm", 3200), areaCode, rows);
    }

    /** 두루누비 — 걷는 길·자전거 길 코스. sigun("부산 영도구") 으로 거른다. */
    public List<Map<String, Object>> trails(String areaCode, int rows) {
        return filterByRegion(national("Durunubi", "courseList", "코스", "sigun", 300), areaCode, rows);
    }

    /** 전국 목록 (분 단위 캐시). 각 항목에 지역 문자열을 _region 으로 실어 둔다. */
    private List<Map<String, Object>> national(String service, String op, String kind,
                                               String regionField, int fetchRows) {
        Cached c = nationalCache.get(service);
        if (c != null && System.currentTimeMillis() - c.at() < TTL_MS) return c.list();

        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", String.valueOf(fetchRows));
        p.put("pageNo", "1");
        List<Map<String, Object>> list = simple(service, op, p, kind, regionField);
        nationalCache.put(service, new Cached(list, System.currentTimeMillis()));
        return list;
    }

    /** 시도별로 고른다. 응답 지역 필드가 풀네임(부산광역시)·약칭(부산 영도구) 섞여서 조각으로 맞춘다. */
    private List<Map<String, Object>> filterByRegion(List<Map<String, Object>> all, String areaCode, int rows) {
        String[] frags = (areaCode == null) ? null : REGION_FRAGMENTS.get(areaCode.trim());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : all) {
            if (frags != null) {
                String region = String.valueOf(m.getOrDefault("_region", ""));
                boolean hit = false;
                for (String f : frags) if (region.contains(f)) { hit = true; break; }
                if (!hit) continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(m);
            copy.remove("_region");     // 내부용 — 화면엔 안 내보낸다
            out.add(copy);
            if (out.size() >= rows) break;
        }
        return out;
    }

    /* KorService2 지역코드 -> 응답 지역 문자열에서 찾을 조각.
       충청·경상·전라는 풀네임(충청남도)과 약칭(충남)이 겹치는 글자가 없어 둘 다 넣는다. */
    private static final Map<String, String[]> REGION_FRAGMENTS = Map.ofEntries(
            Map.entry("1", new String[]{"서울"}), Map.entry("2", new String[]{"인천"}),
            Map.entry("3", new String[]{"대전"}), Map.entry("4", new String[]{"대구"}),
            Map.entry("5", new String[]{"광주"}), Map.entry("6", new String[]{"부산"}),
            Map.entry("7", new String[]{"울산"}), Map.entry("8", new String[]{"세종"}),
            Map.entry("31", new String[]{"경기"}), Map.entry("32", new String[]{"강원"}),
            Map.entry("33", new String[]{"충청북", "충북"}), Map.entry("34", new String[]{"충청남", "충남"}),
            Map.entry("35", new String[]{"경상북", "경북"}), Map.entry("36", new String[]{"경상남", "경남"}),
            Map.entry("37", new String[]{"전북", "전라북"}), Map.entry("38", new String[]{"전남", "전라남"}),
            Map.entry("39", new String[]{"제주"}));

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
        return simple(service, op, params, kind, null);
    }

    /** regionField 를 주면 그 값을 _region 에 실어 둔다 (뒤에서 지역 필터에 쓴다). */
    private List<Map<String, Object>> simple(String service, String op,
                                             Map<String, String> params, String kind, String regionField) {
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
                if (regionField != null) m.put("_region", it.path(regionField).asText(""));
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
