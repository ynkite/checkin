package idusw.sbb.checkin.domain.tour.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관광공사 5종 API 공통 저수준 호출기.
 * 공통 파라미터·재시도 1회·resultCode 검사·items 추출을 한 곳에 모은다.
 * 실패 시 빈 노드를 돌려주므로(폴백) 화면 전체가 멈추지 않는다.
 *
 * 응답을 로컬 DB 에 영구 저장하지 않는다. 실시간 호출 이력이 남아야 심사에서 인정된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiClient {

    // 디코딩키를 넣는다. 인코딩키를 넣으면 SERVICE_KEY_IS_NOT_REGISTERED_ERROR.
    /* 쉼표로 여러 개. 공공데이터포털도 일일 트래픽 한도가 있다.
       팀원이 각자 발급받아 이어 붙이면 한도가 찬 키는 건너뛴다.
       디코딩키를 넣는다 — 인코딩키를 넣으면 SERVICE_KEY_IS_NOT_REGISTERED. */
    private idusw.sbb.checkin.global.apikey.KeyRing ring = new idusw.sbb.checkin.global.apikey.KeyRing("tour", "");

    @Value("${tour.api.key:}")
    private void setTourKey(String raw) {
        this.ring = new idusw.sbb.checkin.global.apikey.KeyRing("tour", raw);
    }

    /* 공개 주소라 기본값을 둔다. 설정 파일에 이 줄이 빠진 환경에서 서버 전체가 뜨지 않았다 */
    @Value("${tour.api.base-url:https://apis.data.go.kr/B551011}")
    private String baseUrl;

    private final RestTemplate restTemplate;      // 타임아웃은 AppConfig 에서 설정 (연결 3s·응답 5s)
    private final ObjectMapper objectMapper;

    private static final JsonNode EMPTY = new ObjectMapper().createArrayNode();

    /**
     * {service}/{operation} 을 공통 파라미터와 함께 호출하고 items.item 배열을 돌려준다.
     * 결과가 하나뿐이면 관광공사 API 는 배열이 아닌 객체로 주므로 호출부에서 isArray 로 분기한다.
     *
     * @return items.item (배열 또는 단일 객체). 실패·오류코드면 빈 배열.
     */
    public JsonNode items(String service, String operation, Map<String, String> params) {
        // 공통 파라미터 + 요청별 파라미터를 직접 인코딩한다.
        // UriComponentsBuilder 를 쓰면 디코딩키의 '/' 가 인코딩되지 않아 키가 깨진다
        // (SERVICE_KEY_IS_NOT_REGISTERED). serviceKey 를 직접 인코딩하고 URI 로 넘겨 재인코딩을 막는다.
        Map<String, String> all = new LinkedHashMap<>();
        all.put("serviceKey", ring.current());   // 디코딩키
        all.put("MobileOS", "ETC");
        all.put("MobileApp", "checkin");
        all.put("_type", "json");
        all.putAll(params);

        StringBuilder qs = new StringBuilder();
        all.forEach((k, v) -> qs.append(qs.length() == 0 ? '?' : '&')
                .append(k).append('=')
                .append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
        String url = baseUrl + "/" + service + "/" + operation + qs;

        String raw = get(url, service);
        if (raw == null) return EMPTY;

        try {
            JsonNode root = objectMapper.readTree(raw);
            String code = root.path("response").path("header").path("resultCode").asText();
            if (!"0000".equals(code)) {
                log.warn("[tour] {} 오류코드 {} - {}", service, code,
                        root.path("response").path("header").path("resultMsg").asText());
                return EMPTY;
            }
            JsonNode items = root.path("response").path("body").path("items").path("item");
            return items.isMissingNode() ? EMPTY : items;
        } catch (Exception e) {
            log.warn("[tour] {} 응답 파싱 실패: {}", service, e.getMessage());
            return EMPTY;
        }
    }

    /** 호출 1회 + 실패 시 즉시 재시도 1회. 둘 다 실패하면 null(→ 폴백). */
    private String get(String url, String service) {
        URI uri = URI.create(url);   // 이미 인코딩된 문자열 — RestTemplate 이 재인코딩하지 않도록 URI 로 넘긴다
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return restTemplate.getForObject(uri, String.class);
            } catch (Exception e) {
                log.warn("[tour] {} 호출 실패 ({}차): {}", service, attempt, e.getMessage());
            }
        }
        return null;
    }
}
