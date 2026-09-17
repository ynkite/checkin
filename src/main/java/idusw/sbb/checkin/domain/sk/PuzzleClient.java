package idusw.sbb.checkin.domain.sk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * SK 지오비전 퍼즐 공용 호출기.
 *
 * 데이터셋이 일곱 가지(지하철 혼잡도·장소 혼잡도·국내 여행·주거 생활·
 * 음식점·학원·유동인구)인데 호출 방식은 전부 같다 — GET, appKey 헤더,
 * JSON. 그래서 클래스를 일곱 개 만들지 않고 경로만 받는 하나로 둔다.
 *
 * TMAP 과 키가 다르다. 같은 SK 계정이지만 퍼즐은 따로 신청한다.
 * 키가 비어 있으면 ready() 가 false 를 돌려주고 호출을 아예 안 한다 —
 * 화면은 「연동 전」이라고 쓰고 0 으로 채우지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PuzzleClient {

    @Value("${sk.puzzle.api.key:}")
    private String appKey;

    @Value("${sk.puzzle.api.base-url:https://apis.openapi.sk.com/puzzle}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public boolean ready() {
        return appKey != null && !appKey.isBlank();
    }

    /**
     * @param path  "/subway/congestion/stat/hourly/stations/{id}" 처럼 baseUrl 뒤에 붙는 부분
     * @param query 쿼리 파라미터. 없으면 빈 맵
     * @return 응답 JSON. 키가 없거나 실패하면 null
     */
    public JsonNode get(String path, Map<String, String> query) {
        if (!ready()) return null;

        StringBuilder qs = new StringBuilder();
        if (query != null) {
            query.forEach((k, v) -> {
                if (v == null || v.isBlank()) return;
                qs.append(qs.length() == 0 ? '?' : '&')
                  .append(k).append('=')
                  .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
            });
        }

        String url = baseUrl + path + qs;
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("appKey", appKey);
            h.set("Accept", "application/json");
            String raw = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
            if (raw == null) return null;

            JsonNode root = objectMapper.readTree(raw);
            /* 퍼즐은 실패해도 200 으로 오면서 status 에 코드를 담는다 */
            String status = root.path("status").asText("");
            if (!status.isEmpty() && !"200".equals(status) && !"00".equals(status)) {
                log.warn("[puzzle] {} status={} {}", path, status, root.path("message").asText(""));
                return null;
            }
            return root;

        } catch (Exception e) {
            log.warn("[puzzle] {} 실패: {}", path, e.getMessage());
            return null;
        }
    }
}
