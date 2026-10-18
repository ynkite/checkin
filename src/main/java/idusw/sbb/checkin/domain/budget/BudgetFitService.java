package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.budget.dto.BudgetFitView;
import idusw.sbb.checkin.domain.budget.dto.BudgetLimitCheck;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.route.service.AiRouteService;
import idusw.sbb.checkin.domain.tour.dto.Spot;
import idusw.sbb.checkin.domain.tour.service.KorTourService;
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
 *   3) 유료 관광지·체험을 비싼 것부터 뺀다. 그날 관광지가 하나뿐이면 남긴다
 *   4) 가장 비싼 숙소를 그 숙소 반경 안의 관광공사 숙소 중 공개 요금이 더 싼 곳으로 바꾼다.
 *      좌표가 있는 곳만 — 동선이 크게 깨지지 않게
 *   5) 그래도 넘으면 화면에 「예산보다 N원 많아요」와 더 줄일 곳을 한 줄로 낸다
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
    private static final int STAY_RADIUS_M = 3000;   // 바꿀 숙소를 찾는 반경
    private static final int STAY_LOOKUPS = 6;       // 요금을 물어볼 후보 수 (요청마다 실시간 호출)

    @Value("${budget.meal-per-person:12000}") private long mealPerPerson;

    private final TravelPlanRepository planRepository;
    private final AiRouteService aiRouteService;
    private final BudgetLimitService limitService;
    private final LodgingRateService lodgingRateService;
    private final TransactionTemplate tx;     // 폼이 지연 로딩이라 읽기만 짧은 트랜잭션 안에서 한다
    private final KorTourService korTourService;

    /** 여행에서 필요한 값만 떼어 낸 것. */
    record Snap(long limit, String json, LocalDate start) {}

    /** 조정 결과. changed 가 false 면 json 은 손대지 않은 원본이다. */
    record Fit(String json, long before, long after, List<String> changes, boolean changed) {}

    /** 바꿔 넣을 숙소. price 는 공개된 1박 요금이다. */
    record Stay(String name, long price, double lat, double lng) {}

    /** 일정 생성 마지막에 부른다. 실패해도 원래 일정이 남는다. */
    public void applyTo(Long tripId) {
        try {
            Snap s = snap(tripId);
            if (s.limit() <= 0) return;
            Fit f = fit(s.json(), s.limit(), mealPerPerson);
            if (f.after() > s.limit()) {
                Stay to = cheaperStay(f.json(), s.start());
                if (to != null) f = swapStay(f, to);
            }
            if (f.changed()) aiRouteService.saveAiRouteToDb(tripId, f.json());
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
                    percent(after, limit), changes, overLine(after, limit, c.biggestLabel(), c.biggestAmount()),
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
        dropPaid(days, "cafe", limit, changes, "유료 카페 ");
        dropPaid(days, "tour", limit, changes, "유료 관광지·체험 ");

        if (changes.isEmpty()) return new Fit(json, before, before, List.of(), false);
        return stamp(days, before, changes);
    }

    /** 가장 비싼 숙소를 to 로 바꾼다. 같은 이름(연박)은 모두. to 가 더 싸지 않으면 그대로 돌려준다. */
    static Fit swapStay(Fit f, Stay to) throws Exception {
        ArrayNode days = (ArrayNode) OM.readTree(f.json());
        JsonNode old = priciestStay(days);
        if (old == null || amount(old.path("sub").asText("")) <= to.price()) return f;

        String oldName = old.path("name").asText();
        long saved = 0;
        for (JsonNode day : days) {
            ArrayNode places = (ArrayNode) day.path("places");
            for (int i = 0; i < places.size(); i++) {
                JsonNode p = places.get(i);
                if (!"stay".equals(p.path("type").asText()) || !oldName.equals(p.path("name").asText())) continue;
                saved += amount(p.path("sub").asText("")) - to.price();
                ((ObjectNode) p).put("name", to.name())
                        .put("sub", "숙소 · " + String.format("₩%,d", to.price()))
                        .put("lat", to.lat()).put("lng", to.lng());
                if (i > 0 && places.get(i - 1).has("transit")) places.set(i - 1, OM.createObjectNode().put("transit", "이동"));
            }
        }
        List<String> changes = new ArrayList<>(f.changes());
        changes.add("숙소를 근처 " + to.name() + "(1박 " + won(to.price()) + ")로 바꾸기 −" + won(saved));
        return stamp(days, f.before(), changes);
    }

    /** 예산을 넘으면 한 줄 — 얼마나 넘는지와 무엇을 더 줄일 수 있는지. 안 넘으면 null. */
    static String overLine(long after, long limit, String biggestLabel, long biggestAmount) {
        if (limit <= 0 || after <= limit) return null;
        String line = "예산보다 " + won(after - limit) + " 많아요.";
        return biggestLabel == null ? line
                : line + " " + biggestLabel + "(" + won(biggestAmount) + ")을 바꾸거나 빼면 더 줄일 수 있어요.";
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

    private static Fit stamp(ArrayNode days, long before, List<String> changes) throws Exception {
        ObjectNode meta = ((ObjectNode) days.get(0)).putObject(KEY);
        meta.put("before", before);
        changes.forEach(meta.putArray("changes")::add);
        return new Fit(OM.writeValueAsString(days), before, total(days), changes, true);
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

    /** 그 종류의 유료 장소를 비싼 것부터 예산 안에 들 때까지 뺀다. 관광지는 그날 하나뿐이면 남긴다. */
    private static void dropPaid(ArrayNode days, String type, long limit, List<String> changes, String label) {
        List<JsonNode[]> hits = new ArrayList<>();    // {places 배열, 장소}
        for (JsonNode day : days)
            for (JsonNode p : day.path("places"))
                if (type.equals(p.path("type").asText()) && amount(p.path("sub").asText("")) > 0)
                    hits.add(new JsonNode[]{day.path("places"), p});
        hits.sort(Comparator.comparingLong((JsonNode[] h) -> amount(h[1].path("sub").asText(""))).reversed());

        int n = 0;
        long saved = 0;
        for (JsonNode[] hit : hits) {
            if (total(days) <= limit) break;
            if ("tour".equals(type) && count(hit[0], type) <= 1) continue;
            saved += amount(hit[1].path("sub").asText(""));
            drop((ArrayNode) hit[0], hit[1]);
            n++;
        }
        if (n > 0) changes.add(label + n + "곳 빼기 −" + won(saved));
    }

    private static int count(JsonNode places, String type) {
        int n = 0;
        for (JsonNode p : places) if (type.equals(p.path("type").asText())) n++;
        return n;
    }

    private static JsonNode priciestStay(JsonNode days) {
        JsonNode best = null;
        for (JsonNode day : days)
            for (JsonNode p : day.path("places"))
                if ("stay".equals(p.path("type").asText())
                        && (best == null || amount(p.path("sub").asText("")) > amount(best.path("sub").asText(""))))
                    best = p;
        return best;
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
            return new Snap(limit, json, p.getStartDate());
        });
    }

    /**
     * 가장 비싼 숙소 반경 안에서 공개 요금이 더 싼 관광공사 숙소. 좌표가 없거나 못 찾으면 null.
     * 공개 요금만 쓴다 — 추정 요금으로 바꾸면 「줄었다」는 말이 틀릴 수 있다.
     */
    private Stay cheaperStay(String json, LocalDate start) {
        try {
            JsonNode old = priciestStay(OM.readTree(json));
            if (old == null || start == null) return null;
            double lat = old.path("lat").asDouble(), lng = old.path("lng").asDouble();
            long unit = amount(old.path("sub").asText(""));
            if (lat == 0 || lng == 0 || unit <= 0) return null;

            Stay best = null;
            int asked = 0;
            for (Spot sp : korTourService.nearby(lat, lng, STAY_RADIUS_M, 32, STAY_LOOKUPS * 2)) {
                if (asked >= STAY_LOOKUPS) break;
                if (sp.lat() == 0 || sp.lon() == 0 || sp.name().equals(old.path("name").asText())) continue;
                asked++;
                long fee = lodgingRateService.publicNightly(sp.id(), start);
                if (fee > 0 && fee < unit && (best == null || fee < best.price()))
                    best = new Stay(sp.name(), fee, sp.lat(), sp.lon());
            }
            return best;
        } catch (Exception e) {
            log.warn("[예산 맞춤] 근처 숙소 조회 실패, 숙소 유지: {}", e.getMessage());
            return null;
        }
    }

    private static String won(long v) {
        return String.format("%,d원", v);
    }
}
