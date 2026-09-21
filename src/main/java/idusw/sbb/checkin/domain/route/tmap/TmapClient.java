package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TMAP(SK open API) 저수준 호출기.
 *
 * 쓰는 것 네 가지 —
 *   POI 통합검색      GET  /tmap/pois                 출발지 글자 -> 좌표
 *   자동차 경로       POST /tmap/routes               지금 떠날 때
 *   자동차 예측경로   POST /tmap/routes/prediction     그 시각에 떠날 때 (타임머신)
 *   대중교통 경로     POST /transit/routes            버스·지하철
 *
 * 키가 없으면 호출하지 않고 null 을 돌려준다. 화면은 「이동시간 확인 중」으로
 * 남고 나머지는 그대로 돈다 — 키 하나 없다고 경로 화면이 죽으면 안 된다.
 *
 * 응답을 저장하지 않는다. 실시간 호출 이력이 남아야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmapClient {

    /* 쉼표로 여러 개를 넣을 수 있다. 한도가 찬 키는 건너뛴다 —
       심사 기간에 한 사람 키로 버티다 한도를 넘기면 그 순간부터
       화면이 「연동 전」이 된다. */
    private idusw.sbb.checkin.global.apikey.KeyRing ring;

    @Value("${tmap.api.key:}")
    private void setKey(String raw) {
        this.ring = new idusw.sbb.checkin.global.apikey.KeyRing("tmap", raw);
    }

    @Value("${tmap.api.base-url:https://apis.openapi.sk.com}")
    private String baseUrl;

    private final RestTemplate restTemplate;   // 타임아웃은 AppConfig (연결 3s · 응답 5s)

    /* 경유지 최적화만 유별나게 느리다 — 실측 4.9~5.7초로 전역 5초 제한에 딱 걸려 매번 끊겼다.
       전역을 늘리면 카카오·기상청이 죽을 때 화면이 그만큼 더 멈춘다. 이 API 만 따로 둔다 */
    private final RestTemplate slowTemplate = new org.springframework.boot.web.client.RestTemplateBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(3))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .build();
    private final ObjectMapper objectMapper;

    /** 키가 들어와 있는가. 화면에 「연동 전」이라고 정직하게 쓰려면 알아야 한다. */
    public boolean ready() {
        return ring.ready();
    }

    /** POI 통합검색 — 「부산역」 같은 글자를 좌표로. 첫 결과만 본다. */
    public JsonNode pois(String keyword, int count) {
        if (!ready() || keyword == null || keyword.isBlank()) return null;
        Map<String, String> q = new LinkedHashMap<>();
        q.put("version", "1");
        q.put("searchKeyword", keyword);
        q.put("count", String.valueOf(Math.max(1, Math.min(20, count))));
        q.put("resCoordType", "WGS84GEO");
        q.put("searchType", "all");
        return get("/tmap/pois", q);
    }

    /** 주변 검색 — 현재 좌표 반경에서 「주유소·화장실·편의점」 같은 걸 찾는다. */
    public JsonNode poisAround(double lat, double lon, String keyword, int radiusKm, int count) {
        if (!ready()) return null;
        Map<String, String> q = new LinkedHashMap<>();
        q.put("version", "1");
        q.put("centerLat", String.valueOf(lat));
        q.put("centerLon", String.valueOf(lon));
        q.put("radius", String.valueOf(Math.max(1, Math.min(33, radiusKm)))); // km, 최대 33
        q.put("count", String.valueOf(Math.max(1, Math.min(20, count))));
        q.put("reqCoordType", "WGS84GEO");
        q.put("resCoordType", "WGS84GEO");
        if (keyword != null && !keyword.isBlank()) q.put("searchKeyword", keyword);
        return get("/tmap/pois/search/around", q);
    }

    /**
     * 자동차 경로. departAt 이 있으면 예측경로(타임머신)로 부른다.
     * searchOption 0 = 교통최적+추천.
     */
    public JsonNode carRoute(double sx, double sy, double ex, double ey,
                             String startName, String endName, String departAt) {
        if (!ready()) return null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", sx);
        body.put("startY", sy);
        body.put("endX", ex);
        body.put("endY", ey);
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("searchOption", "0");
        body.put("trafficInfo", "Y");
        body.put("startName", enc(startName));
        body.put("endName", enc(endName));

        String path = "/tmap/routes?version=1";
        if (departAt != null && !departAt.isBlank()) {
            /* 타임머신 — 그 시각의 교통량으로 계산한다.
               내일 오후 2시에 떠날 때의 30분이 지금의 30분과 다르다. */
            body.put("predictionType", "departure");
            body.put("predictionTime", departAt);   // 2026-09-19T14:00:00+0900
            path = "/tmap/routes/prediction?version=1";
        }
        return post(path, body);
    }

    /**
     * 경유지 최적화 — 출발/도착 사이 여러 곳을 가장 짧게 도는 순서로 재정렬.
     * viaPoints: 각 {viaPointId, viaPointName, viaX(경도), viaY(위도)}. 최대 10곳.
     */
    public JsonNode routeOptimization(double sx, double sy, double ex, double ey,
                                      String startName, String endName,
                                      java.util.List<Map<String, Object>> viaPoints) {
        if (!ready()) return null;
        Map<String, Object> body = new LinkedHashMap<>();
        // 좌표는 문자열로 넣는다 — 숫자로 주면 TMAP 이 500 을 낸다.
        body.put("startX", String.valueOf(sx));
        body.put("startY", String.valueOf(sy));
        body.put("endX", String.valueOf(ex));
        body.put("endY", String.valueOf(ey));
        body.put("startName", enc(startName));
        body.put("endName", enc(endName));
        // startTime 은 필수. 지금 출발 기준(현재 시각, YYYYMMDDHHmm).
        body.put("startTime", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm")));
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("searchOption", "0");
        // viaPointName URL 인코딩, viaX/viaY 도 문자열로.
        java.util.List<Map<String, Object>> vps = new java.util.ArrayList<>();
        for (Map<String, Object> v : viaPoints) {
            Map<String, Object> e = new LinkedHashMap<>(v);
            Object nm = v.get("viaPointName");
            e.put("viaPointName", enc(nm == null ? "경유지" : nm.toString()));
            e.put("viaX", String.valueOf(v.get("viaX")));
            e.put("viaY", String.valueOf(v.get("viaY")));
            vps.add(e);
        }
        body.put("viaPoints", vps);
        return post("/tmap/routes/routeOptimization10?version=1", body);
    }

    /** 대중교통 경로. 길이 없으면 result.status 로 온다. */
    public JsonNode transitRoute(double sx, double sy, double ex, double ey) {
        if (!ready()) return null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", String.valueOf(sx));
        body.put("startY", String.valueOf(sy));
        body.put("endX", String.valueOf(ex));
        body.put("endY", String.valueOf(ey));
        body.put("count", 1);
        body.put("lang", 0);
        body.put("format", "json");
        return post("/transit/routes", body);
    }

    /** 보행자 경로 — 한 정거장 안에서 걷는 구간. */
    public JsonNode walkRoute(double sx, double sy, double ex, double ey,
                              String startName, String endName) {
        if (!ready()) return null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", sx);
        body.put("startY", sy);
        body.put("endX", ex);
        body.put("endY", ey);
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("startName", enc(startName));
        body.put("endName", enc(endName));
        body.put("speed", 4);
        return post("/tmap/routes/pedestrian?version=1", body);
    }

    /**
     * 좌표를 가장 가까운 도로에 붙이고 그 도로 링크의 속성을 돌려준다.
     * resultData.header 에 speed(제한속도 km/h) · lane(차선 수) · roadName · roadCategory 가 온다.
     *
     * 경로 응답(/tmap/routes)에는 제한속도도 차선수도 없다. 주행 화면에 숫자를 띄우려면
     * 이걸 따로 불러야 한다. 좌표가 도로에서 멀면 엉뚱한 옆길에 붙으니, 부르는 쪽에서
     * 돌아온 roadName 이 기대한 도로와 같은지 확인하고 쓴다.
     */
    public JsonNode nearToRoad(double lat, double lon) {
        if (!ready()) return null;
        Map<String, String> q = new LinkedHashMap<>();
        q.put("version", "1");
        q.put("lat", String.valueOf(lat));
        q.put("lon", String.valueOf(lon));
        q.put("coordType", "WGS84GEO");
        return get("/tmap/road/nearToRoad", q);
    }

    /** 좌표 -> 주소. 「지금 위치」 버튼이 쓴다 */
    public JsonNode reverseGeo(double lat, double lon) {
        if (!ready()) return null;
        Map<String, String> q = new java.util.LinkedHashMap<>();
        q.put("version", "1");
        q.put("lat", String.valueOf(lat));
        q.put("lon", String.valueOf(lon));
        q.put("coordType", "WGS84GEO");
        q.put("addressType", "A10");
        return get("/tmap/geo/reversegeocoding", q);
    }

    /* ── 바닥 ──────────────────────────────────────────────── */

    /** TMAP 은 startName·endName 을 URL 인코딩한 값으로 받는다. */
    private String enc(String s) {
        return URLEncoder.encode(s == null || s.isBlank() ? "출발" : s, StandardCharsets.UTF_8);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("appKey", ring.current());
        h.set("Accept", "application/json");
        return h;
    }

    private JsonNode get(String path, Map<String, String> q) {
        StringBuilder qs = new StringBuilder();
        q.forEach((k, v) -> qs.append(qs.length() == 0 ? '?' : '&')
                .append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return call(HttpMethod.GET, path + qs, null);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return call(HttpMethod.POST, path, body);
    }

    /* 403 을 받은 API 경로. 「이 키로는 그 상품을 못 쓴다」는 뜻이라 다시 불러도 같은 답이다 */
    private final java.util.Set<String> forbidden = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private JsonNode call(HttpMethod method, String path, Object body) {
        String api = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        if (forbidden.contains(api)) return null;

        String url = baseUrl + path;
        try {
            HttpEntity<Object> req = new HttpEntity<>(body, headers());
            RestTemplate rt = api.contains("routeOptimization") ? slowTemplate : restTemplate;
            String raw = rt.exchange(URI.create(url), method, req, String.class).getBody();
            return raw == null ? null : objectMapper.readTree(raw);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            /* 한도가 찼거나(429) 키가 거부되면(401·403) 다음 키로 넘기고
               한 번 더 부른다. 서버가 느린 것(5xx)으로는 키를 죽이지 않는다 */
            int st = e.getStatusCode().value();

            /* 403 으로는 키를 죽이지 않는다. SK 게이트웨이의 403 은 「한도 초과」가 아니라
               「이 앱키가 그 상품을 안 샀다」는 뜻이다. 대중교통(/transit/routes)은 TMAP 과
               별도 상품이라 우리 키로는 403 이 온다 — 그런데 그걸 키 소진으로 보고 키를
               통째로 꺼 버려서, 대중교통 여행 하나만 뽑으면 그날 자차 경로·이동시간·경유지
               최적화까지 다 죽었다. 안 되는 것은 그 API 하나뿐이다.
               다른 키로 다시 불러도 같은 답이므로(키가 다 같은 상품이다) 재시도도 하지 않는다 */
            if (st == 403) {
                if (forbidden.add(api)) {
                    log.warn("[tmap] {} 는 이 앱키로 쓸 수 없습니다 (403). 이 API 만 끕니다 —"
                            + " 나머지 티맵 기능은 그대로 씁니다. 쓰려면 SK OPEN API 에서 해당 상품을 받아야 합니다", api);
                }
                return null;
            }

            if (ring.fail(st)) {
                log.info("[tmap] 다음 키로 다시 부릅니다 ({})", path);
                return call(method, path, body);
            }
            log.warn("[tmap] {} 실패 HTTP {}", path, st);
            return null;
        } catch (Exception e) {
            /* 길이 없거나 SK 가 느릴 때다. 여기서 터지면 경로 화면
               전체가 죽는다. 로그만 남기고 없음으로 돌려준다. */
            log.warn("[tmap] {} 실패: {}", path, e.getMessage());
            return null;
        }
    }
}
