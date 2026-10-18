package idusw.sbb.checkin.domain.expense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.budget.BudgetFitService;
import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 동선 JSON 한 벌 → 가계부 「예상 지출」 줄.
 *
 * 저장할 때 DB 에 넣는 것과 가계부가 보여 주는 것이 같은 함수다. 전에는 저장 때 만든 줄만
 * 읽어서, 일정이 고쳐지기 전에 저장된 숙박 줄(2박인데 4줄)이 가계부에 그대로 남았다.
 */
@Slf4j
public final class EstimatedExpenses {

    private static final ObjectMapper OM = new ObjectMapper();

    private EstimatedExpenses() {}

    public static List<Expense> from(TravelPlan plan, String json) {
        List<Expense> out = new ArrayList<>();
        if (json == null || json.isBlank() || plan.getStartDate() == null) return out;
        try {
            JsonNode root = OM.readTree(json);
            if (!root.isArray()) return out;
            for (JsonNode dayNode : root) {
                LocalDate date = plan.getStartDate().plusDays(dayNode.path("day").asInt(1) - 1);
                long transitOfDay = 0;
                int legs = 0;
                for (JsonNode place : dayNode.path("places")) {
                    if (!place.has("type")) {   // 이동 구간은 장소가 아니다. 그 날 몫을 한 줄로 합친다
                        transitOfDay += BudgetFitService.amount(place.path("transit").asText(""));
                        legs++;
                        continue;
                    }
                    String category = category(place.path("type").asText(""));
                    long amount = BudgetFitService.amount(place.path("sub").asText(""));
                    if (category == null || amount <= 0) continue;
                    out.add(row(plan, category, place.path("name").asText(""), amount, date));
                }
                if (transitOfDay > 0) out.add(row(plan, "TRANSPORT", "이동 " + legs + "구간", transitOfDay, date));
            }
        } catch (Exception e) {
            log.warn("[가계부] 예상 지출을 못 읽음: {}", e.getMessage());
        }
        return out;
    }

    private static Expense row(TravelPlan plan, String category, String desc, long amount, LocalDate date) {
        return Expense.builder().plan(plan).user(plan.getUser()).category(category)
                .description(desc).amount(amount).isEstimated(true).expenseDate(date).build();
    }

    private static String category(String type) {
        return switch (type.toLowerCase()) {
            case "stay" -> "STAY";
            case "food" -> "FOOD";
            case "tour" -> "TOUR";
            case "cafe" -> "CAFE";
            default -> null;
        };
    }
}
