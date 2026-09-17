package idusw.sbb.checkin.domain.crowd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * SK 실시간 인원 — 지금 그 장소에 사람이 얼마나 있는가.
 *
 * 관광공사 집중률은 「예측」이다. 여행 날 아침에 필요한 것은 지금 값이다.
 * 이 클래스가 그 자리를 맡는다.
 *
 * 설정 (없으면 꺼진 상태로 돈다) —
 *   sk.realtime.url          호출 주소. {q} 자리에 장소 이름,
 *                            {lat} {lng} 자리에 좌표가 들어간다
 *   sk.realtime.key          발급받은 키
 *   sk.realtime.key-header   키를 실을 헤더 이름 (기본 appKey)
 *   sk.realtime.path.level   응답에서 혼잡 단계를 꺼낼 경로 (점으로 구분)
 *   sk.realtime.path.count   응답에서 인원수를 꺼낼 경로
 *   sk.realtime.level-scale  단계가 1~N 중 N (기본 4)
 *
 * 왜 경로를 설정으로 두는가 — 키를 받기 전에는 응답 모양을 확정할 수 없다.
 * 모양을 코드에 박아 두면 키가 와도 코드를 다시 짜야 한다. 경로만 적으면 붙는다.
 *
 * 단계를 0~100 집중률 눈금으로 바꿔서 화면이 관광공사 값과 같은 다섯 단계로
 * 읽게 한다. 1/4 -> 12.5, 2/4 -> 37.5, 3/4 -> 62.5, 4/4 -> 87.5.
 *
 * 응답을 저장하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkRealtimeCrowdClient {

    @Value("${sk.realtime.url:}")
    private String url;

    @Value("${sk.realtime.key:}")
    private String key;

    @Value("${sk.realtime.key-header:appKey}")
    private String keyHeader;

    @Value("${sk.realtime.path.level:}")
    private String levelPath;

    @Value("${sk.realtime.path.count:}")
    private String countPath;

    @Value("${sk.realtime.level-scale:4}")
    private int levelScale;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 지금 값을 물을 수 있는 상태인가. */
    public boolean ready() {
        return url != null && !url.isBlank()
                && key != null && !key.isBlank()
                && levelPath != null && !levelPath.isBlank();
    }

    /** 실시간 인원. 단계·인원수·집중률 눈금으로 환산한 값. 못 물으면 null. */
    public record Live(int level, int levelScale, Integer headcount, double asRate) {}

    public Live now(String placeName, Double lat, Double lng) {
        if (!ready()) return null;
        String target = url
                .replace("{q}", enc(placeName))
                .replace("{lat}", lat == null ? "" : String.valueOf(lat))
                .replace("{lng}", lng == null ? "" : String.valueOf(lng));
        try {
            HttpHeaders h = new HttpHeaders();
            h.set(keyHeader, key);
            h.set("Accept", "application/json");
            String raw = restTemplate.exchange(URI.create(target), HttpMethod.GET,
                    new HttpEntity<>(h), String.class).getBody();
            if (raw == null) return null;

            JsonNode root = objectMapper.readTree(raw);
            JsonNode lv = at(root, levelPath);
            if (lv == null || lv.isMissingNode() || lv.isNull()) return null;

            int level = readLevel(lv);
            if (level <= 0) return null;
            Integer count = null;
            if (countPath != null && !countPath.isBlank()) {
                JsonNode c = at(root, countPath);
                if (c != null && c.isNumber()) count = c.asInt();
            }
            int scale = Math.max(2, levelScale);
            /* 단계 한가운데를 집중률로 본다. 4단계면 12.5 / 37.5 / 62.5 / 87.5 */
            double rate = (level - 0.5) / scale * 100.0;
            return new Live(level, scale, count, rate);
        } catch (Exception e) {
            log.warn("[sk] 실시간 인원 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /* 숫자로 오면 그대로, 글자로 오면 우리가 아는 말로 맞춘다.
       서울 실시간 도시데이터처럼 「여유·보통·약간 붐빔·붐빔」으로 주는 곳이 있다. */
    private int readLevel(JsonNode lv) {
        if (lv.isNumber()) return lv.asInt();
        String s = lv.asText("").trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        if (s.contains("여유")) return 1;
        if (s.contains("보통")) return 2;
        if (s.contains("약간")) return 3;
        if (s.contains("붐빔") || s.contains("혼잡")) return 4;
        return 0;
    }

    /** 점으로 구분한 경로를 따라 내려간다. 배열은 숫자 조각으로 짚는다 — a.0.b */
    private JsonNode at(JsonNode root, String path) {
        JsonNode cur = root;
        for (String part : path.split("\\.")) {
            if (cur == null) return null;
            if (part.matches("\\d+")) cur = cur.path(Integer.parseInt(part));
            else cur = cur.path(part);
        }
        return cur;
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
