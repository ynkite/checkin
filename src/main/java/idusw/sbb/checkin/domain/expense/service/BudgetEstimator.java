package idusw.sbb.checkin.domain.expense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate.Item;
import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.tour.dto.LodgingRate;
import idusw.sbb.checkin.domain.tour.dto.Spot;
import idusw.sbb.checkin.domain.tour.service.KorTourService;
import idusw.sbb.checkin.domain.tour.service.LodgingRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예산 엔진 — 숙박비를 항목으로 쪼개고 확정·추정·해당없음을 붙인다 (홍은표).
 *
 * 숙박 예약에서 사용자가 겪는 문제는 재고가 아니라 요금 규칙이다.
 * 최초 표시가와 결제가가 벌어지는 이유(인원 추가·바베큐·레이트 체크아웃)를
 * 항목으로 드러내고, 각 항목이 공개 데이터인지 추정인지 같이 보여 준다.
 *
 * 확정의 근거는 관광공사 detailInfo2 의 공개 요금뿐이다.
 * 그 밖의 금액은 전부 추정이고, 추정을 확정처럼 표기하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetEstimator {

    /* 평균 기준 단가 — 관광공사 응답에 금액 필드가 없는 항목들.
       화면에는 「평균 기준」이라고 적는다. 인원 추가는 객실 요금이 있으면
       거기서 1인 몫을 내고, 요금이 없을 때만 아래 값을 쓴다. */
    static final long EXTRA_PERSON_PER_NIGHT = 20_000;   // 정원 초과 1인 1박 (객실 요금 모를 때)
    static final long BARBECUE               = 30_000;   // 1회
    static final long LATE_CHECKOUT          = 10_000;   // 1회
    static final long PET_PER_NIGHT          = 20_000;   // 1박

    /** 추정 항목이 흔들릴 수 있는 폭. 구간 표기의 근거이자 화면에 적는 값. */
    static final double ESTIMATE_BAND = 0.20;

    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern WON = Pattern.compile("₩([\\d,]+)(?:×(\\d+))?");

    private final KorTourService korTourService;
    private final LodgingRateService lodgingRateService;

    /**
     * 계산에 필요한 값만 모은 입력. 화면·엔티티와 떨어져 있어 그대로 검증할 수 있다.
     *
     * @param rate              1박 요금 (이수환 산출물). 숙소를 못 찾았으면 null
     * @param nights            숙박 일수
     * @param people            인원
     * @param baseCount         숙소 기준인원 (공개 데이터). 0 이면 모름
     * @param barbecue          사용자가 바베큐 옵션을 골랐는지
     * @param barbecueAvailable 숙소가 바베큐 제공이라고 공개 데이터에 밝혔는지
     * @param lateCheckout      레이트 체크아웃 옵션
     * @param pet               반려동물 동반
     * @param scheduleLodging   일정에 적힌 숙박비 합 (가계부 숙박 예상과 같은 값). 0 이면 일정에 없음
     */
    public record Input(
            LodgingRate rate,
            int nights,
            int people,
            int baseCount,
            boolean barbecue,
            boolean barbecueAvailable,
            boolean lateCheckout,
            boolean pet,
            long scheduleLodging
    ) {
        /** 필요한 객실 수 — 인원 ÷ 기준인원 올림. 기준인원을 모르면 객실 2인 기준 */
        int rooms() {
            return (int) Math.max(1, Math.ceil(people / (double) (baseCount > 0 ? baseCount : 2)));
        }
    }

    /** 여행 한 건의 숙박비 예측. 외부 API 가 죽어도 항목이 「없음」으로 내려갈 뿐 화면은 뜬다. */
    public BudgetEstimate estimate(TravelPlan plan) {
        return compute(toInput(plan));
    }

    // ── 계산 (순수 함수 — 외부 호출 없음) ──

    static BudgetEstimate compute(Input in) {
        List<Item> items = new ArrayList<>();
        items.add(roomFee(in));
        items.add(extraPeople(in));
        items.add(barbecue(in));
        items.add(lateCheckout(in));
        items.add(pet(in));

        long total = items.stream().mapToLong(Item::amount).sum();
        long confirmed = items.stream()
                .filter(i -> i.status() == BudgetEstimate.Status.CONFIRMED)
                .mapToLong(Item::amount).sum();
        long estimated = items.stream()
                .filter(i -> i.status() == BudgetEstimate.Status.ESTIMATED)
                .mapToLong(Item::amount).sum();

        // 내림이다. 99.5% 를 100% 로 올리면 「전부 확정」이라는 거짓말이 된다 — 신뢰도는 올려 말하지 않는다
        int confidence = total == 0 ? 0 : (int) Math.floor(confirmed * 100.0 / total);
        long band = Math.round(estimated * ESTIMATE_BAND);

        // 화면 머리말이 「확정 = 실제 요금, 추정 = 평균 기준」을 한 번 말한다. 여기서 되풀이하지 않는다
        String note = total == 0 ? "공개된 요금 자료를 찾지 못했습니다." : "";

        return new BudgetEstimate(items, total, total - band, total + band, confidence, note);
    }

    /**
     * 객실 기본료 — 일정에 숙박비가 있으면 그 값이다. 가계부 「숙박」 예상과 한 계산에서 나오게.
     * 공개 요금 × 객실 수 × 박수가 그 값과 같을 때만 확정이다.
     */
    private static Item roomFee(Input in) {
        LodgingRate rate = in.rate();
        int nights = Math.max(1, in.nights());
        if (in.scheduleLodging() > 0) {
            boolean exact = rate != null && !rate.estimated()
                    && rate.amount() * in.rooms() * nights == in.scheduleLodging();
            return exact
                    ? Item.confirmed("객실 기본료", in.scheduleLodging(),
                        "실제 요금 1실 " + won(rate.amount()) + " × " + in.rooms() + "실 × " + nights + "박")
                    : Item.estimated("객실 기본료", in.scheduleLodging(), "일정 숙박비 · " + nights + "박");
        }
        if (rate == null || rate.amount() <= 0) {
            return Item.none("객실 기본료", "공개 요금 없음");
        }
        long amount = rate.amount() * nights;
        String basis = rate.season() + " · " + nights + "박";
        return rate.estimated()
                ? Item.estimated("객실 기본료", amount, basis)
                : Item.confirmed("객실 기본료", amount, basis);
    }

    /**
     * 인원 추가 — 초과 인원 수는 공개된 기준인원에서 나오지만 1인당 금액은 공개되지 않는다.
     * 그래서 항목 전체는 추정이고, 어디까지가 공개 데이터인지를 근거 문구에 적는다.
     */
    private static Item extraPeople(Input in) {
        if (in.scheduleLodging() > 0) return Item.none("인원 추가", in.people() + "명 · 객실 기본료에 포함");
        // 기준인원을 모르면 객실 2인 기준으로 보고 평균 금액을 쓴다
        boolean known = in.baseCount() > 0;
        int base = known ? in.baseCount() : 2;
        String baseText = known ? "기준인원 " + base + "명" : "객실 2인 기준";

        /* 1인 몫 = 객실 요금 ÷ 기준인원 (천 원 단위 반올림). 요금표에서 나온 값이지만
           숙소가 받는 실제 추가요금은 공개되지 않으므로 항목은 추정으로 둔다 */
        LodgingRate r = in.rate();
        boolean fromRate = known && r != null && r.amount() > 0;
        long perHead = fromRate ? Math.round(r.amount() / (double) base / 1000) * 1000 : EXTRA_PERSON_PER_NIGHT;

        int over = in.people() - base;
        if (over <= 0) return Item.none("인원 추가", baseText + " 이내 · 넘으면 1인 " + won(perHead));
        long amount = (long) over * perHead * Math.max(1, in.nights());
        return Item.estimated("인원 추가 " + over + "명", amount,
                baseText + " 초과 · 1인 " + won(perHead)
                        + (fromRate ? " (객실 " + won(r.amount()) + " ÷ " + base + "명)" : ""));
    }

    /** 바베큐 — 공개 데이터는 「가능한지」만 알려 준다. 금액은 끝까지 추정으로 남는다. */
    private static Item barbecue(Input in) {
        if (!in.barbecue()) return Item.none("바베큐", "선택 안 함");
        if (in.scheduleLodging() > 0) return onSite("바베큐", BARBECUE);
        return Item.estimated("바베큐", BARBECUE,
                in.barbecueAvailable() ? "숙소 제공 확인" : "선택함");
    }

    private static Item lateCheckout(Input in) {
        if (!in.lateCheckout()) return Item.none("레이트 체크아웃", "선택 안 함");
        if (in.scheduleLodging() > 0) return onSite("레이트 체크아웃", LATE_CHECKOUT);
        return Item.estimated("레이트 체크아웃", LATE_CHECKOUT, "선택함");
    }

    private static Item pet(Input in) {
        if (!in.pet()) return Item.none("반려동물", "동반 없음");
        if (in.scheduleLodging() > 0) return onSite("반려동물", PET_PER_NIGHT * Math.max(1, in.nights()));
        return Item.estimated("반려동물", PET_PER_NIGHT * Math.max(1, in.nights()), "동반 추가요금");
    }

    /** 일정 숙박비에 없는 선택 항목 — 현장에서 따로 내는 돈이라 총액에 넣지 않고 금액만 알린다 */
    private static Item onSite(String label, long amount) {
        return Item.none(label, "선택함 · 현장에서 약 " + won(amount) + " (총액 별도)");
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    // ── 입력 모으기 (여기서만 외부 API 를 탄다) ──

    private Input toInput(TravelPlan plan) {
        PlanInputForm form = plan.getForm();
        int nights = nights(plan);
        int people = (form != null && form.getCompanionCount() != null) ? form.getCompanionCount() : 2;
        String options = (form != null && form.getAccommodationOptions() != null)
                ? form.getAccommodationOptions() : "";
        boolean pet = form != null && form.getHasPet() == 1;
        boolean barbecue = options.contains("바베큐");
        boolean lateCheckout = options.contains("레이트 체크아웃");

        // 가계부·예산 맞춤과 같은 일정을 본다 — 확정된 여행을 고치는 중이면 고치는 쪽
        String json = "FIXED".equals(plan.getStatus()) && plan.getDraftRouteJson() != null
                ? plan.getDraftRouteJson() : plan.getRouteJson();
        long lodging = scheduleLodging(json);
        Spot stay = findStay(json);
        if (stay == null) {
            return new Input(null, nights, people, 0, barbecue, false, lateCheckout, pet, lodging);
        }

        // 관광공사 응답은 로컬 DB 에 저장하지 않는다 — 요청마다 실시간 호출이다.
        LodgingRate rate = lodgingRateService.nightly(stay.id(), stay.areaCode(), plan.getStartDate());
        return new Input(rate, nights, people,
                lodgingRateService.baseCount(stay.id()),
                barbecue,
                barbecue && lodgingRateService.barbecueAvailable(stay.id()),
                lateCheckout, pet, lodging);
    }

    /** 숙박 일수. 당일치기면 1박으로 본다(요금 항목이 0이 되면 화면이 빈다). */
    static int nights(TravelPlan plan) {
        if (plan.getStartDate() == null || plan.getEndDate() == null) return 1;
        long n = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate());
        return (int) Math.max(1, n);
    }

    /** 동선의 숙소 이름으로 관광공사 숙박(32) 을 찾는다. 못 찾으면 null 이고 항목은 「없음」이 된다. */
    private Spot findStay(String routeJson) {
        String name = stayName(routeJson);
        if (name == null) return null;
        try {
            List<Spot> found = korTourService.searchByName(name, 32, 1);
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            log.warn("[budget] 숙소 조회 실패 ({}): {}", name, e.getMessage());
            return null;
        }
    }

    /** routeJson 의 첫 type=stay 이름. 구조가 다르거나 비면 null. */
    static String stayName(String routeJson) {
        JsonNode stay = firstStay(routeJson);
        return stay == null ? null : stay.path("name").asText();
    }

    /** 일정의 숙소 금액 합 — 가계부가 숙박 예상을 내는 것과 같은 셈 (sub 의 ₩금액, ×N 이면 곱한다) */
    static long scheduleLodging(String routeJson) {
        long sum = 0;
        if (routeJson == null || routeJson.isBlank()) return 0;
        try {
            for (JsonNode day : OM.readTree(routeJson))
                for (JsonNode place : day.path("places")) {
                    if (!"stay".equals(place.path("type").asText())) continue;
                    Matcher m = WON.matcher(place.path("sub").asText(""));
                    if (m.find()) sum += Long.parseLong(m.group(1).replace(",", ""))
                            * (m.group(2) == null ? 1 : Long.parseLong(m.group(2)));
                }
        } catch (Exception e) {
            log.warn("[budget] routeJson 파싱 실패: {}", e.getMessage());
        }
        return sum;
    }

    private static JsonNode firstStay(String routeJson) {
        if (routeJson == null || routeJson.isBlank()) return null;
        try {
            for (JsonNode day : OM.readTree(routeJson))
                for (JsonNode place : day.path("places"))
                    if ("stay".equals(place.path("type").asText()) && !place.path("name").asText("").isBlank())
                        return place;
        } catch (Exception e) {
            log.warn("[budget] routeJson 파싱 실패: {}", e.getMessage());
        }
        return null;
    }
}
