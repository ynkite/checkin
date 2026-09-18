package idusw.sbb.checkin.domain.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 동선 JSON 이 쓸 만한가.
 *
 * <p>왜 필요한가 — 장소 교체와 실내 전환이 모델이 돌려준 글을 그대로 결과로 삼았다.
 * 빈 응답이나 대괄호 없는 글이 오면 {@code "[]"} 가 성공 결과가 되어 DB 에 저장되고
 * 화면 세션까지 덮어썼다. 사용자는 3단계에서 일정을 만들었는데 4단계가 빈 화면이 됐다.
 * 브라우저 콘솔에 남은 것이 이것이다 —
 * 「[지도] 세션에 든 경로에 들를 곳이 없습니다. []」
 *
 * <p>지도 화면의 {@code _mpUsable} 과 같은 기준이다. 두 곳이 다른 기준을 쓰면
 * 서버는 「됐다」는데 화면은 비는 일이 또 난다. 기준을 바꿀 일이 있으면 양쪽을 같이 바꿔라.
 *
 * <p>「값이 없으면 없다고 말한다」가 이 제품의 규칙인데, 여기서는 한 걸음 더 간다 —
 * <b>원본보다 나쁜 것을 결과로 삼지 않는다.</b> 잘못된 장소가 남아 있는 편이
 * 아무것도 없는 화면보다 낫다.
 */
public final class RouteJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RouteJson() {}

    /**
     * 배열이고, 하루라도 있고, 이름이 있는 들를 곳이 하나라도 있는가.
     * 이동(transit) 칸만 있는 하루는 들를 곳으로 치지 않는다.
     */
    public static boolean usable(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray() || root.isEmpty()) return false;

            for (JsonNode day : root) {
                JsonNode places = day.path("places");
                if (!places.isArray()) continue;
                for (JsonNode place : places) {
                    if (place.hasNonNull("transit")) continue;
                    if (!place.path("name").asText("").isBlank()) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;   /* 파싱이 안 되면 쓸 수 없는 것이다 */
        }
    }
}
