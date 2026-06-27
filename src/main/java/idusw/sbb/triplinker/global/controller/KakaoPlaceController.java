package idusw.sbb.triplinker.global.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.triplinker.global.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 카카오맵(로컬) 키워드 검색 컨트롤러.
 * 사용자가 모달에서 '다른 숙소명'을 직접 입력했을 때, 카카오맵에 실존하는
 * 정확한 상호명으로 보정하는 데 사용한다. (AI 호출 없이 정확·토큰 절약)
 */
@RestController
@RequestMapping("/api/kakao")
public class KakaoPlaceController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.rest.api.key}")
    private String kakaoRestKey;

    public KakaoPlaceController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // 예: /api/kakao/place?query=해운대 그랜드호텔&region=부산
    @GetMapping("/place")
    public ApiResponse<Object> searchPlace(
            @RequestParam String query,
            @RequestParam(required = false) String region) {
        try {
            // 지역을 앞에 붙이면 동명 숙소 오인식이 줄어든다(예: "부산 그랜드호텔")
            String q = (region != null && !region.isBlank()) ? region + " " + query : query;

            String url = "https://dapi.kakao.com/v2/local/search/keyword.json?query="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoRestKey);
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<String> res = restTemplate.exchange(
                    url, HttpMethod.GET, req, String.class);

            JsonNode docs = objectMapper.readTree(res.getBody()).path("documents");
            if (!docs.isArray() || docs.isEmpty()) {
                // 못 찾으면 사용자가 입력한 원본 이름을 그대로 반환
                return ApiResponse.success(Map.of("name", query, "matched", false));
            }

            JsonNode first = docs.get(0);
            return ApiResponse.success(Map.of(
                    "name", first.path("place_name").asText(query),     // 카카오 공식 상호명
                    "address", first.path("road_address_name").asText(""),
                    "matched", true
            ));
        } catch (Exception e) {
            // 호출 실패 시에도 서비스가 끊기지 않도록 원본 이름 반환
            return ApiResponse.success(Map.of("name", query, "matched", false));
        }
    }
}