package idusw.sbb.checkin.domain.route.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 동선을 바꿨을 때의 시간·돈 쌍 — `−40분 / +3,200원` (예산 엔진 2층 · 00_통합기획서 4장).
 *
 * 동선 변경 제안에는 소요시간 변화와 비용 변화를 **항상 쌍으로** 붙인다.
 * 둘 중 하나만 보여 주면 사용자는 「시간은 줄었는데 돈은?」 을 다시 물어야 한다.
 *
 * 값은 이미 routeJson 안에 있다. AiRouteService.recalcTransitWithKakao 가 저장 직전에
 * 구간마다 `🚌 대중교통 · 8.4km · 약 25분 · ₩1,600` 을 써 넣는다.
 * 새로 재지 않고 저장된 동선 두 벌을 비교한다 — 외부 호출이 없으니 실패할 곳도 없다.
 *
 * @param minutes 이동시간 변화(분). 음수면 줄었다는 뜻. 잴 수 없으면 null
 * @param won     비용 변화(원). 음수면 줄었다는 뜻. 잴 수 없으면 null
 */
public record RouteDelta(Integer minutes, Long won) {

    /** `약 25분` */
    private static final Pattern MINUTES = Pattern.compile("약\\s*(\\d+)\\s*분");
    /** `₩12,000×2` — 배수는 인원수다. RouteJsonWriter·AiRouteService 와 같은 규칙 (셋이 같이 바뀌어야 한다). */
    private static final Pattern AMOUNT = Pattern.compile("₩([\\d,]+)(?:×(\\d+))?");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String STAY = "stay";

    /** 잴 수 없는 쪽은 0 이 아니라 null 로 남긴다 — 「변화 없음」과 「모름」은 다르다. */
    public static RouteDelta between(String before, String after) {
        Totals b = sum(before);
        Totals a = sum(after);
        return new RouteDelta(
                (b.minutes() == null || a.minutes() == null) ? null : a.minutes() - b.minutes(),
                (b.won()     == null || a.won()     == null) ? null : a.won()     - b.won());
    }

    /** 화면에 뜰 게 하나도 없으면 줄 자체를 내보내지 않는다. 응답 JSON 에는 안 싣는다. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return minutes == null && won == null;
    }

    /**
     * 동선 한 벌의 합계. 이동 구간은 시간과 요금을, 장소는 금액을 낸다.
     * 일자의 budget 은 이 둘의 합이라 같이 더하면 두 번 센다 — 건드리지 않는다.
     */
    static Totals sum(String routeJson) {
        if (routeJson == null || routeJson.isBlank()) return new Totals(null, null);
        try {
            JsonNode root = MAPPER.readTree(routeJson);
            if (!root.isArray()) return new Totals(null, null);

            int minutes = 0;  long won = 0;
            int transits = 0, measuredMinutes = 0, measuredFares = 0;

            for (JsonNode day : root) {
                JsonNode places = day.path("places");
                for (int i = 0; i < places.size(); i++) {
                    JsonNode place = places.get(i);

                    if (place.has("transit")) {
                        String text = place.path("transit").asText("");
                        transits++;
                        Matcher m = MINUTES.matcher(text);
                        if (m.find()) { minutes += Integer.parseInt(m.group(1)); measuredMinutes++; }
                        Matcher f = AMOUNT.matcher(text);
                        if (f.find()) { won += amount(f); measuredFares++; }
                        continue;
                    }

                    // 숙소는 하루의 맨 앞과 맨 뒤에 같은 행이 한 번씩 붙는다 (RouteJsonWriter.writeDay).
                    // 2박이면 stay 행이 4개다 — 맨 앞 행은 어제 묵은 숙소이니 여기서 세지 않는다
                    if (i == 0 && STAY.equals(place.path("type").asText(""))) continue;

                    // 첫 매치 하나만 — 화면의 일자 budget 과 같은 규칙이다
                    // (RouteJsonWriter.parseAmount · AiRouteService.parseAmountFromSub).
                    // 범위 표기(₩10,000~15,000)에서 둘 다 더하면 화면 금액과 어긋난다
                    Matcher m = AMOUNT.matcher(place.path("sub").asText(""));
                    if (m.find()) won += amount(m);
                }
            }

            // 한 구간이라도 못 잰 동선은 합계가 아니다. 다음 스냅샷에서 그 구간이 채워지면
            // 사용자가 만들지 않은 시간·요금이 통째로 변화로 잡힌다
            boolean allMeasured = transits > 0 && measuredMinutes == transits && measuredFares == transits;
            return new Totals(allMeasured ? minutes : null, allMeasured ? won : null);
        } catch (Exception e) {
            // 동선을 못 읽어도 교체 자체는 성공한 것이다. 쌍만 빠진다
            return new Totals(null, null);
        }
    }

    private static long amount(Matcher m) {
        long value = Long.parseLong(m.group(1).replace(",", ""));
        return m.group(2) != null ? value * Long.parseLong(m.group(2)) : value;
    }

    record Totals(Integer minutes, Long won) {}
}
