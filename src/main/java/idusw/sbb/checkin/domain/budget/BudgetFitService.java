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
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예산 맞춤 — 일정이 만들어진 뒤 총액을 사용자가 정한 예산과 견주고, 넘으면 줄인다.
 *
 * 목표는 「예산을 넘지 않으면서 가장 가깝게」다. 많이 줄이는 것이 아니다.
 * 한 번에 하나씩, 그것 하나로 예산 안에 들어오는 것 중 가장 작은 것을 고르고,
 * 그런 것이 없으면 가장 큰 것부터 줄인다. 줄이는 순서:
 *   1) 끼니 1인 단가가 평균(budget.meal-per-person)보다 비싼 곳을 평균으로 낮춘다
 *   2) 유료 카페를 뺀다 — 빠진 자리의 이동 구간은 저장할 때 카카오가 다시 잰다
 *   3) 유료 관광지·체험을 뺀다. 그날 관광지가 하나뿐이면 남긴다
 *   4) 그래도 넘으면 가장 비싼 숙소를 그 숙소 반경 안의 관광공사 숙소로 바꿔 본다.
 *      후보마다 바꾼 뒤 1)~3)을 다시 돌려, 예산 이하이면서 가장 가까운 결과를 고른다.
 *      요금은 공개된 1실 요금 × 필요한 객실 수(인원 ÷ 기준인원 올림)다.
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
    private static final int STAY_RADIUS_M = 3000;    // 바꿀 숙소를 찾는 반경
    private static final int STAY_LOOKUPS = 6;        // 요금을 물어볼 후보 수 (요청마다 실시간 호출)
    static final long MIN_PER_PERSON = 15_000;        // 1인 1박이 이보다 싸면 1인·1실 요금으로 보고 뺀다

    @Value("${budget.meal-per-person:12000}") private long mealPerPerson;

    private final TravelPlanRepository planRepository;
    private final AiRouteService aiRouteService;
    private final BudgetLimitService limitService;
    private final LodgingRateService lodgingRateService;
    private final TransactionTemplate tx;     // 폼이 지연 로딩이라 읽기만 짧은 트랜잭션 안에서 한다
    private final KorTourService korTourService;

    /** 여행에서 필요한 값만 떼어 낸 것. */
    record Snap(long limit, String json, LocalDate start, int people) {}

    /** 조정 결과. changed 가 false 면 json 은 손대지 않은 원본이다. */
    record Fit(String json, long before, long after, List<String> changes, boolean changed) {}

    /** 바꿔 넣을 숙소. price 는 일행 1박 값 = 공개된 1실 요금 × rooms. */
    record Stay(String name, long price, int rooms, double lat, double lng) {}

    /** 일정 생성 마지막에 부른다. 실패해도 원래 일정이 남는다. */
    public void applyTo(Long tripId) {
        try {
            Snap s = snap(tripId);
            if (s.limit() <= 0) return;
            Fit f = fit(s.json(), s.limit(), mealPerPerson);
            if (f.after() > s.limit())
                f = best(s.json(), s.limit(), mealPerPerson, nearbyStays(s.json(), s.start(), s.people()));
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
        cheaperMeals(days, limit, mealCap, changes);
        dropPaid(days, "cafe", limit, changes, "유료 카페 ");
        dropPaid(days, "tour", limit, changes, "유료 관광지·체험 ");

        if (changes.isEmpty()) return new Fit(json, before, before, List.of(), false);
        return stamp(days, before, changes);
    }

    /** 숙소 후보마다 바꿔 보고 다시 맞춰, 예산 이하이면서 가장 가까운 결과. 후보가 없으면 fit 결과. */
    static Fit best(String json, long limit, long mealCap, List<Stay> stays) throws Exception {
        Fit best = fit(json, limit, mealCap);
        if (best.after() <= limit) return best;
        long before = total(OM.readTree(json));
        for (Stay s : stays) {
            ArrayNode days = (ArrayNode) OM.readTree(json);
            String change = swapStay(days, s);
            if (change == null) continue;
            Fit f = fit(OM.writeValueAsString(days), limit, mealCap);
            List<String> changes = new ArrayList<>(List.of(change));
            changes.addAll(f.changes());
            Fit cand = stamp((ArrayNode) OM.readTree(f.json()), before, changes);
            if (closer(cand.after(), best.after(), limit)) best = cand;
        }
        return best;
    }

    /** a 가 b 보다 나은가 — 예산 안이 먼저, 둘 다 안이면 더 큰 쪽, 둘 다 넘으면 더 작은 쪽. */
    static boolean closer(long a, long b, long limit) {
        if ((a <= limit) != (b <= limit)) return a <= limit;
        return a <= limit ? a > b : a < b;
    }

    /**
     * 공개된 1실 요금 · 기준인원으로 일행 1박 값을 낸다. 기준인원을 모르면 2인실로 본다.
     * 1인 1박이 MIN_PER_PERSON 보다 싸면 1인·1실 요금이 잘못 들어온 것으로 보고 null.
     */
    static Stay partyStay(String name, long fee, int base, int people, double lat, double lng) {
        int n = Math.max(1, people);
        int rooms = (int) Math.ceil(n / (double) (base > 0 ? base : 2));
        long price = fee * rooms;
        return fee <= 0 || price < MIN_PER_PERSON * n ? null : new Stay(name, price, rooms, lat, lng);
    }

    /**
     * 가장 비싼 숙소를 to 로 바꾼다. 같은 이름(연박)은 모두 — 금액이 붙은 줄(밤)만 값을 바꾸고,
     * 아침 출발 줄은 이름·좌표만. to 가 더 싸지 않으면 null. 바꾼 내용 한 줄을 돌려준다.
     */
    static String swapStay(ArrayNode days, Stay to) {
        JsonNode old = priciestStay(days);
        if (old == null || amount(old.path("sub").asText("")) <= to.price()) return null;

        String oldName = old.path("name").asText();
        long saved = 0;
        for (JsonNode day : days) {
            ArrayNode places = (ArrayNode) day.path("places");
            for (int i = 0; i < places.size(); i++) {
                JsonNode p = places.get(i);
                if (!"stay".equals(p.path("type").asText()) || !oldName.equals(p.path("name").asText())) continue;
                ObjectNode o = ((ObjectNode) p).put("name", to.name()).put("lat", to.lat()).put("lng", to.lng());
                long was = amount(p.path("sub").asText(""));
                if (was > 0) {
                    saved += was - to.price();
                    o.put("sub", "숙소 · " + (to.rooms() > 1 ? to.rooms() + "실 · " : "") + String.format("₩%,d", to.price()));
                }
                if (i > 0 && places.get(i - 1).has("transit")) places.set(i - 1, OM.createObjectNode().put("transit", "이동"));
            }
        }
        return "숙소를 근처 " + to.name() + "(" + (to.rooms() > 1 ? to.rooms() + "실, " : "")
                + "1박 " + won(to.price()) + ")로 바꾸기 −" + won(saved);
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

    /**
     * 남은 후보 중 하나 — 그것 하나로 예산 안에 드는 것 중 가장 작은 것, 없으면 가장 큰 것.
     * 많이 줄이는 것이 목표가 아니라 예산에 가깝게 멈추는 것이 목표라서다.
     */
    private static <T> T pick(List<T> list, ToLongFunction<T> gain, long over) {
        T fit = null, big = null;
        for (T t : list) {
            long g = gain.applyAsLong(t);
            if (g >= over && (fit == null || g < gain.applyAsLong(fit))) fit = t;
            if (big == null || g > gain.applyAsLong(big)) big = t;
        }
        return fit != null ? fit : big;
    }

    private static void cheaperMeals(ArrayNode days, long limit, long cap, List<String> changes) {
        List<ObjectNode> meals = new ArrayList<>();
        for (JsonNode day : days)
            for (JsonNode p : day.path("places"))
                if ("food".equals(p.path("type").asText()) && unit(p) > cap) meals.add((ObjectNode) p);
        ToLongFunction<ObjectNode> gain = p -> amount(p.path("sub").asText("")) - cap * count(p);

        int n = 0;
        long saved = 0;
        while (!meals.isEmpty() && total(days) > limit) {
            ObjectNode p = pick(meals, gain, total(days) - limit);
            meals.remove(p);
            saved += gain.applyAsLong(p);
            Matcher m = WON.matcher(p.path("sub").asText(""));
            m.find();
            p.put("sub", p.path("sub").asText().replace(m.group(0),
                    String.format("₩%,d", cap) + (m.group(2) == null ? "" : "×" + m.group(2))));
            n++;
        }
        if (n > 0) changes.add("식사 " + n + "끼를 1인 " + won(cap) + " 안팎 메뉴로 (평균 기준) −" + won(saved));
    }

    /** 그 종류의 유료 장소를 예산 안에 들 때까지 하나씩 뺀다. 관광지는 그날 하나뿐이면 남긴다. */
    private static void dropPaid(ArrayNode days, String type, long limit, List<String> changes, String label) {
        List<JsonNode[]> hits = new ArrayList<>();    // {places 배열, 장소}
        for (JsonNode day : days)
            for (JsonNode p : day.path("places"))
                if (type.equals(p.path("type").asText()) && amount(p.path("sub").asText("")) > 0)
                    hits.add(new JsonNode[]{day.path("places"), p});

        int n = 0;
        long saved = 0;
        while (total(days) > limit) {
            hits.removeIf(h -> "tour".equals(type) && kinds(h[0], type) <= 1);
            if (hits.isEmpty()) break;
            JsonNode[] hit = pick(hits, h -> amount(h[1].path("sub").asText("")), total(days) - limit);
            hits.remove(hit);
            saved += amount(hit[1].path("sub").asText(""));
            drop((ArrayNode) hit[0], hit[1]);
            n++;
        }
        if (n > 0) changes.add(label + n + "곳 빼기 −" + won(saved));
    }

    private static long unit(JsonNode p) {
        Matcher m = WON.matcher(p.path("sub").asText(""));
        return m.find() ? Long.parseLong(m.group(1).replace(",", "")) : 0;
    }

    private static long count(JsonNode p) {
        Matcher m = WON.matcher(p.path("sub").asText(""));
        return m.find() && m.group(2) != null ? Long.parseLong(m.group(2)) : 1;
    }

    private static int kinds(JsonNode places, String type) {
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
            int people = p.getForm() == null || p.getForm().getCompanionCount() == null ? 2 : p.getForm().getCompanionCount();
            String json = "FIXED".equals(p.getStatus()) && p.getDraftRouteJson() != null
                    ? p.getDraftRouteJson() : p.getRouteJson();
            return new Snap(limit, json, p.getStartDate(), people);
        });
    }

    /** 가장 비싼 숙소 반경 안의 관광공사 숙소 중 공개 요금이 있고 현실적인 곳. 실패하면 빈 목록. */
    private List<Stay> nearbyStays(String json, LocalDate start, int people) {
        List<Stay> out = new ArrayList<>();
        try {
            JsonNode old = priciestStay(OM.readTree(json));
            if (old == null || start == null) return out;
            double lat = old.path("lat").asDouble(), lng = old.path("lng").asDouble();
            if (lat == 0 || lng == 0) return out;

            int asked = 0;
            for (Spot sp : korTourService.nearby(lat, lng, STAY_RADIUS_M, 32, STAY_LOOKUPS * 2)) {
                if (asked >= STAY_LOOKUPS) break;
                if (sp.lat() == 0 || sp.lon() == 0 || sp.name().equals(old.path("name").asText())) continue;
                asked++;
                long[] room = lodgingRateService.publicRoom(sp.id(), start);
                Stay s = partyStay(sp.name(), room[0], (int) room[1], people, sp.lat(), sp.lon());
                if (s != null) out.add(s);
            }
        } catch (Exception e) {
            log.warn("[예산 맞춤] 근처 숙소 조회 실패, 숙소 유지: {}", e.getMessage());
        }
        return out;
    }

    private static String won(long v) {
        return String.format("%,d원", v);
    }
}
