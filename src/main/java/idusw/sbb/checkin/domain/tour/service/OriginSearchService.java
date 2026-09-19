package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 출발지 검색 — 카카오 로컬 키워드 검색, 비면 주소 검색.
 *
 * 같은 이름이 여럿일 때 고를 수 있게 도로명 주소·지번 주소·업종을 같이 준다.
 * 「못 찾음」(NONE)과 「검색을 못 함」(UNAVAILABLE)을 나눠서 돌려준다.
 * 화면이 둘을 같은 문구로 말하면 사용자는 철자를 고치느라 시간을 버린다.
 */
@Slf4j
@Service
public class OriginSearchService {

    public enum Status { OK, NONE, UNAVAILABLE }

    public record Place(String name, String roadAddress, String address, String category,
                        double lat, double lng, Integer distanceM) {}

    public record Result(Status status, String query, List<Place> items) {}

    static final int MAX = 7;
    private static final String KEYWORD = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String ADDRESS = "https://dapi.kakao.com/v2/local/search/address.json";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kakao.rest.api.key:}")
    private String kakaoKey;

    public OriginSearchService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    void setKakaoKey(String key) { this.kakaoKey = key; }

    public Result search(String rawQuery, Double lat, Double lng) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (kakaoKey == null || kakaoKey.isBlank()) return new Result(Status.UNAVAILABLE, q, List.of());

        boolean near = lat != null && lng != null && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
        String keywordUrl = KEYWORD + "?size=" + MAX + "&query=" + enc(q)
                + (near ? String.format(Locale.ROOT, "&x=%.6f&y=%.6f", lng, lat) : "");

        JsonNode docs = call(keywordUrl);
        if (docs == null) return new Result(Status.UNAVAILABLE, q, List.of());

        List<Place> out = new ArrayList<>();
        for (JsonNode d : docs) {
            String cat = d.path("category_group_name").asText("");
            if (cat.isBlank()) {
                /* 업종 묶음이 없는 곳은 「음식점 > 한식 > 국밥」의 마지막 칸을 쓴다 */
                String full = d.path("category_name").asText("");
                cat = full.contains(">") ? full.substring(full.lastIndexOf('>') + 1).trim() : full;
            }
            String dist = d.path("distance").asText("");
            out.add(new Place(
                    d.path("place_name").asText(""),
                    d.path("road_address_name").asText(""),
                    d.path("address_name").asText(""),
                    cat,
                    d.path("y").asDouble(), d.path("x").asDouble(),
                    dist.isBlank() ? null : parseIntOrNull(dist)));
        }

        /* 「해운대구 우동 1418」처럼 주소를 친 경우는 키워드 검색이 비는 일이 많다 */
        if (out.isEmpty()) {
            JsonNode addr = call(ADDRESS + "?size=" + MAX + "&query=" + enc(q));
            if (addr == null) return new Result(Status.UNAVAILABLE, q, List.of());
            for (JsonNode d : addr) {
                String road = d.path("road_address").path("address_name").asText("");
                String jibun = d.path("address").path("address_name").asText(d.path("address_name").asText(""));
                String building = d.path("road_address").path("building_name").asText("");
                out.add(new Place(
                        !building.isBlank() ? building : (!road.isBlank() ? road : jibun),
                        road, jibun, "주소",
                        d.path("y").asDouble(), d.path("x").asDouble(), null));
            }
        }
        out.removeIf(p -> p.lat() == 0 && p.lng() == 0);
        return new Result(out.isEmpty() ? Status.NONE : Status.OK, q, out);
    }

    /**
     * 좌표가 속한 시군구를 「부산 해운대구」 꼴로. AreaCode.find 가 그대로 받는 모양이다.
     * 여행지가 「부산」처럼 시도만이면 AreaCode 는 첫 시군구(중구)를 고르므로, 좌표로 바로잡는 데 쓴다.
     * 못 받으면 null.
     */
    public String regionAt(double lat, double lng) {
        if (kakaoKey == null || kakaoKey.isBlank()) return null;
        JsonNode docs = call(String.format(Locale.ROOT,
                "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=%.6f&y=%.6f", lng, lat));
        if (docs == null || docs.isEmpty()) return null;
        JsonNode d = docs.get(0);
        String sido = shortSido(d.path("region_1depth_name").asText(""));
        String gu = d.path("region_2depth_name").asText("");
        return (sido == null || gu.isBlank()) ? null : sido + " " + gu;
    }

    /* 카카오는 「경상북도」「부산광역시」로 준다. AreaCode 는 「경북」「부산」을 쓴다 */
    static String shortSido(String full) {
        if (full == null || full.isBlank()) return null;
        return switch (full) {
            case "충청북도" -> "충북";
            case "충청남도" -> "충남";
            case "전라북도" -> "전북";
            case "전라남도" -> "전남";
            case "경상북도" -> "경북";
            case "경상남도" -> "경남";
            default -> full.length() >= 2 ? full.substring(0, 2) : null;
        };
    }

    /** documents 배열. 호출이 실패하면 null — 「결과 없음」과 구분한다 */
    private JsonNode call(String url) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("Authorization", "KakaoAK " + kakaoKey);
            String body = restTemplate.exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
            JsonNode docs = objectMapper.readTree(body == null ? "{}" : body).path("documents");
            return docs.isArray() ? docs : null;
        } catch (Exception e) {
            log.warn("[origin-search] 카카오 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Integer parseIntOrNull(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
}
