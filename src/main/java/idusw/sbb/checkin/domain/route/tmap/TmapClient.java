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

    private JsonNode call(HttpMethod method, String path, Object body) {
        String url = baseUrl + path;
        try {
            HttpEntity<Object> req = new HttpEntity<>(body, headers());
            String raw = restTemplate.exchange(URI.create(url), method, req, String.class).getBody();
            return raw == null ? null : objectMapper.readTree(raw);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            /* 한도가 찼거나(429) 키가 거부되면(401·403) 다음 키로 넘기고
               한 번 더 부른다. 서버가 느린 것(5xx)으로는 키를 죽이지 않는다 */
            int st = e.getStatusCode().value();
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
