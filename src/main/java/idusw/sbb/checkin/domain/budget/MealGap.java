package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Pattern;

/**
 * 끼니 시간에 식당이 없고 「○○골목」「○○시장」 같은 먹거리 거리가 관광지 ₩0 으로 들어간 날 —
 * 그 줄을 식당으로 돌리고 1인 평균 끼니값을 매긴다. 54번 여행 1일차 18:00
 * 「해운대곰장어골목」이 관광지 ₩0×4 로 잡혀 저녁값이 0원이던 원인이다
 * (AI 지시가 먹거리 거리를 맛집으로 넣지 말라고 해서 관광지로 들어온다).
 */
public final class MealGap {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern FOOD_STREET = Pattern.compile("골목|시장|먹자|맛집|포차|야시장");

    private MealGap() {}

    public static String fill(String json, int people, long mealPerPerson) {
        try {
            JsonNode root = OM.readTree(json);
            boolean changed = false;
            for (JsonNode day : root) {
                changed |= fillMeal(day.path("places"), "점심", 11 * 60, 14 * 60, people, mealPerPerson);
                changed |= fillMeal(day.path("places"), "저녁", 17 * 60, 20 * 60 + 30, people, mealPerPerson);
            }
            return changed ? OM.writeValueAsString(root) : json;
        } catch (Exception e) {
            return json;
        }
    }

    private static boolean fillMeal(JsonNode places, String meal, int from, int to, int people, long cost) {
        ObjectNode street = null;
        for (JsonNode p : places) {
            int t = minutes(p.path("time").asText(""));
            if (t < from || t > to) continue;
            String type = p.path("type").asText();
            if ("food".equals(type)) return false;
            if (street == null && "tour".equals(type) && BudgetFitService.amount(p.path("sub").asText("")) == 0
                    && FOOD_STREET.matcher(p.path("name").asText("")).find()) street = (ObjectNode) p;
        }
        if (street == null || cost <= 0) return false;
        street.put("type", "food").put("icon", "🍽️")
              .put("sub", "맛집 · " + meal + " · " + String.format("₩%,d×%d", cost, Math.max(1, people)) + " · 평균 끼니값");
        return true;
    }

    private static int minutes(String hhmm) {
        int c = hhmm.indexOf(':');
        try {
            return c <= 0 ? -1 : Integer.parseInt(hhmm.substring(0, c).trim()) * 60 + Integer.parseInt(hhmm.substring(c + 1, c + 3));
        } catch (Exception e) {
            return -1;
        }
    }
}
