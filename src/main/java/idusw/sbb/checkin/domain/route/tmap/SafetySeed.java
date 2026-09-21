package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 어린이보호구역 같은 안전 지점 표본.
 *
 * 원래는 공공데이터포털 「전국어린이보호구역표준데이터」를 써야 한다. 지금 프로젝트에 있는
 * 두 키(tour.api.key · weather.api.key)로는 그 API 가 열려 있지 않다 — 실제로 불러 보면
 * SERVICE_KEY_IS_NOT_REGISTERED_ERROR 가 온다. data.go.kr 은 API 마다 따로 활용신청을
 * 해야 열리기 때문이다. 그때까지 쓸 표본을 static/data 에 두고 뜰 때 한 번만 읽는다.
 *
 * 파일이 없거나 깨져도 서버는 그대로 뜬다. 안전 지점만 비어 있을 뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafetySeed {

    private static final String PATH = "static/data/road-safety-seed.json";

    /** 안전 지점 하나. radiusMeters 안으로 경로가 지나가면 알려 준다. */
    public record Spot(String kind, String name, double lat, double lng,
                       Integer speedLimit, String note, int radiusMeters) {}

    private final ObjectMapper objectMapper;

    private List<Spot> spots = List.of();

    /** 이 표본이 어디서 온 것인지 — 응답의 alertSource 에 그대로 쓴다. */
    private String source = "연동 전";

    @PostConstruct
    void load() {
        ClassPathResource res = new ClassPathResource(PATH);
        if (!res.exists()) {
            log.info("[navi] 안전 지점 표본 파일이 없습니다 ({}). 안전 알림 없이 갑니다", PATH);
            return;
        }
        try (InputStream in = res.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            List<Spot> out = new ArrayList<>();
            for (JsonNode n : root.path("spots")) {
                String kind = n.path("kind").asText("");
                double lat = n.path("lat").asDouble(0), lng = n.path("lng").asDouble(0);
                if (kind.isBlank() || lat == 0 || lng == 0) continue;
                Integer limit = n.hasNonNull("speedLimit") ? n.path("speedLimit").asInt() : null;
                out.add(new Spot(kind,
                        n.path("name").asText(""),
                        lat, lng, limit,
                        n.path("note").asText(""),
                        Math.max(20, n.path("radiusMeters").asInt(40))));
            }
            this.spots = List.copyOf(out);
            this.source = out.isEmpty() ? "연동 전"
                    : "표본 데이터 — 부산 해운대·센텀·광안리 일대 학교 앞만. 공공데이터 연동 전입니다";
            log.info("[navi] 안전 지점 표본 {}곳 읽었습니다", out.size());
        } catch (Exception e) {
            log.warn("[navi] 안전 지점 표본을 못 읽었습니다: {}", e.getMessage());
        }
    }

    public List<Spot> spots() {
        return spots;
    }

    public String source() {
        return source;
    }
}
