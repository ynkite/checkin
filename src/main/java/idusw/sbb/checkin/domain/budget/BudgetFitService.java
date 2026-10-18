package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.budget.dto.BudgetFitView;
import idusw.sbb.checkin.domain.budget.dto.BudgetLimitCheck;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.route.service.AiRouteService;
import idusw.sbb.checkin.domain.tour.dto.LodgingRate;
import idusw.sbb.checkin.domain.tour.service.LodgingRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예산 맞춤 — 일정이 만들어진 뒤 총액을 사용자가 정한 예산과 견주고, 넘으면 줄인다.
 *
 * 줄이는 순서 (넘는 만큼만):
 *   1) 끼니 1인 단가가 평균(budget.meal-per-person)보다 비싸면 평균으로 낮춘다
 *   2) 유료 카페를 비싼 것부터 뺀다 — 빠진 자리의 이동 구간은 저장할 때 카카오가 다시 잰다
 *   3) 그래도 넘으면 숙소는 바꾸지 않고, 관광공사 지역 평균 요금과 견준 제안만 남긴다.
 *      다른 숙소를 마음대로 넣으면 동선이 무너진다.
 *
 * 조정 전 총액·바꾼 내용은 1일차 칸의 budgetFit 에 남긴다 (화면이 「전 → 후」를 그린다).
 * 무엇이 실패하든 원래 일정을 그대로 둔다. 시연 중 오류 화면을 띄우지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetFitService {

    static final String KEY = "budgetFit";
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern WON = Pattern.compile("₩([\\d,]+)(?:×(\\d+))?");

    @Value("${budget.meal-per-person:12000}") private long mealPerPerson;

    private final TravelPlanRepository planRepository;
    private final AiRouteService aiRouteService;
    private final BudgetLimitService limitService;
    private final LodgingRateService lodgingRateService;
    private final TransactionTemplate tx;     // 폼이 지연 로딩이라 읽기만 짧은 트랜잭션 안에서 한다

    /** 여행에서 필요한 값만 떼어 낸 것. */
    record Snap(long limit, String json, String destination, LocalDate start) {}

    /** 조정 결과. changed 가 false 면 json 은 손대지 않은 원본이다. */
    record Fit(String json, long before, long after, List<String> changes, boolean changed) {}

    /** 일정 생성 마지막에 부른다. 실패해도 원래 일정이 남는다. */
    public void applyTo(Long tripId) {
        try {
            Snap s = snap(tripId);
            if (s.limit() <= 0) return;
            Fit f = fit(s.json(), s.limit(), mealPerPerson);
            String suggest = f.after() > s.limit() ? lodgingSuggest(s, f.json()) : null;
            if (!f.changed() && suggest == null) return;
            String json = suggest == null ? f.json() : withSuggest(f.json(), suggest);
            aiRouteService.saveAiRouteToDb(tripId, json);
        } catch (Exception e) {
            log.warn("[예산 맞춤] tripId={} 조정 못 함, 원래 일정 유지: {}", tripId, e.getMessage());
        }
    }

    /** 화면용. 일정을 못 읽으면 fallback=true 로 비워 보낸다 — 화면은 가계부 총액으로 % 를 낸다. */
    public BudgetFitView view(Long tripId) {
        long limit = 0;
        try {
            Snap s = snap(tripId);
            limit = s.limit();
            ArrayNode days = (ArrayNode) OM.readTree(s.json());
            long after = total(days);
            JsonNode meta = days.path(0).path(KEY);
            List<String> changes = new ArrayList<>();
            meta.path("changes").forEach(c -> changes.add(c.asText()));
            BudgetLimitCheck c = limitService.check(after, items(days), limit);
            return new BudgetFitView(c.hasLimit(), limit, meta.path("before").asLong(after), after,
                    percent(after, limit), changes, meta.path("suggest").asText(null),
                    c.biggestLabel(), c.biggestAmount(), false);
        } catch (Exception e) {
            log.warn("[예산 맞춤] tripId={} 일정을 못 읽음: {}", tripId, e.getMessage());
            return new BudgetFitView(limit > 0, limit, 0, 0, 0, List.of(), null, null, 0, true);
        }
    }

    // ── 계산 (순수 함수 — 외부 호출 없음) ──

    static Fit fit(String json, long limit, long mealCap) throws Exception {
        ArrayNode days = (ArrayNode) OM.readTree(json);
        long before = total(days);
        if (limit <= 0 || before <= limit) return new Fit(json, before, before, List.of(), false);

        List<String> changes = new ArrayList<>();
        long meals = cheaperMeals(days, mealCap);
        if (meals > 0) changes.add("식사를 1인 " + won(mealCap) + " 안팎 메뉴로 (평균 기준) −" + won(meals));

        int cafes = 0;
        long cafeSaved = 0;
        for (JsonNode[] hit : paidCafes(days)) {
            if (total(days) <= limit) break;
            cafeSaved += amount(hit[1].path("sub").asText(""));
            drop((ArrayNode) hit[0], hit[1]);
            cafes++;
        }
        if (cafes > 0) changes.add("유료 카페 " + cafes + "곳 빼기 −" + won(cafeSaved));

        long after = total(days);
        if (changes.isEmpty()) return new Fit(json, before, after, List.of(), false);

        ObjectNode meta = ((ObjectNode) days.get(0)).putObject(KEY);
        meta.put("before", before);
        changes.forEach(meta.putArray("changes")::add);
        return new Fit(OM.writeValueAsString(days), before, after, changes, true);
    }

    /** 장소 sub · 이동 구간 금액의 합. 가계부 예상 비용과 같은 기준이다. */
    static long total(JsonNode days) {
        long sum = 0;
        for (JsonNode day : days)
            for (JsonNode p : day.path("places"))
                sum += amount(p.path(p.has("type") ? "sub" : "transit").asText(""));
        return sum;
    }

    static int percent(long total, long limit) {
        return limit <= 0 ? 0 : (int) Math.round(total * 100.0 / limit);
    }

    private static long cheaperMeals(JsonNode days, long cap) {
        long saved = 0;
        for (JsonNode day : days)
            for (JsonNode p : day.path("places")) {
                if (!"food".equals(p.path("type").asText())) continue;
                String sub = p.path("sub").asText("");
                Matcher m = WON.matcher(sub);
                if (!m.find()) continue;
                long unit = Long.parseLong(m.group(1).replace(",", ""));
                if (unit <= cap) continue;
                long cnt = m.group(2) == null ? 1 : Long.parseLong(m.group(2));
                saved += (unit - cap) * cnt;
                ((ObjectNode) p).put("sub", sub.replace(m.group(0),
                        String.format("₩%,d", cap) + (m.group(2) == null ? "" : "×" + cnt)));
            }
        return saved;
    }

    /** {places 배열, 카페} 쌍. 비싼 것부터. */
    private static List<JsonNode[]> paidCafes(JsonNode days) {
        List<JsonNode[]> out = new ArrayList<>();
        for (JsonNode day : days)
            for (JsonNode p : day.path("places"))
                if ("cafe".equals(p.path("type").asText()) && amount(p.path("sub").asText("")) > 0)
                    out.add(new JsonNode[]{day.path("places"), p});
        out.sort(Comparator.comparingLong((JsonNode[] h) -> amount(h[1].path("sub").asText(""))).reversed());
        return out;
    }

    /** 장소와 붙은 이동 구간 하나를 같이 뺀다. 남은 구간은 「이동」으로 비워 저장 때 다시 잰다. */
    private static void drop(ArrayNode places, JsonNode place) {
        int i = 0;
        while (places.get(i) != place) i++;
        places.remove(i);
        if (i < places.size() && places.get(i).has("transit")) places.remove(i);
        else if (i > 0 && places.get(i - 1).has("transit")) places.remove(--i);
        if (i > 0 && places.get(i - 1).has("transit")) places.set(i - 1, OM.createObjectNode().put("transit", "이동"));
    }

    private static List<BudgetEstimate.Item> items(JsonNode days) {
        List<BudgetEstimate.Item> out = new ArrayList<>();
        for (JsonNode day : days)
            for (JsonNode p : day.path("places"))
                if (p.has("type"))
                    out.add(BudgetEstimate.Item.estimated(p.path("type").asText(), p.path("name").asText(),
                            amount(p.path("sub").asText("")), "일정 금액", null));
        return out;
    }

    static long amount(String s) {
        Matcher m = WON.matcher(s == null ? "" : s);
        if (!m.find()) return 0;
        long v = Long.parseLong(m.group(1).replace(",", ""));
        return m.group(2) == null ? v : v * Long.parseLong(m.group(2));
    }

    // ── 입력 · 외부 호출 ──

    private Snap snap(Long tripId) {
        return tx.execute(st -> {
            TravelPlan p = planRepository.findById(tripId)
                    .orElseThrow(() -> new IllegalArgumentException("여행 없음"));
            long limit = p.getForm() == null || p.getForm().getBudget() == null ? 0 : p.getForm().getBudget();
            String json = "FIXED".equals(p.getStatus()) && p.getDraftRouteJson() != null
                    ? p.getDraftRouteJson() : p.getRouteJson();
            return new Snap(limit, json, p.getDestination(), p.getStartDate());
        });
    }

    /** 숙소 1박이 관광공사 지역 평균보다 비싸면 제안 한 줄. 바꾸지는 않는다. 조회가 안 되면 null. */
    private String lodgingSuggest(Snap s, String json) {
        try {
            long stay = 0;
            for (JsonNode day : OM.readTree(json))
                for (JsonNode p : day.path("places"))
                    if ("stay".equals(p.path("type").asText())) stay = Math.max(stay, amount(p.path("sub").asText("")));
            String area = AreaCode.tourAreaCode(AreaCode.find(s.destination()));
            if (stay <= 0 || area == null) return null;
            LodgingRate avg = lodgingRateService.regional(area, s.start());
            if (avg.amount() <= 0 || avg.amount() >= stay) return null;
            return "숙소를 이 지역 평균 가격대(1박 " + won(avg.amount()) + ", 관광공사 공개 요금 "
                    + avg.sampleSize() + "곳)로 고르면 1박에 " + won(stay - avg.amount()) + " 줄어듭니다.";
        } catch (Exception e) {
            log.warn("[예산 맞춤] 숙소 평균 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private static String withSuggest(String json, String suggest) throws Exception {
        ArrayNode days = (ArrayNode) OM.readTree(json);
        ObjectNode day0 = (ObjectNode) days.get(0);
        ObjectNode meta = day0.has(KEY) ? (ObjectNode) day0.get(KEY) : day0.putObject(KEY);
        if (!meta.has("before")) meta.put("before", total(days));
        meta.put("suggest", suggest);
        return OM.writeValueAsString(days);
    }

    private static String won(long v) {
        return String.format("%,d원", v);
    }
}
