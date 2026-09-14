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

    /* 추정 단가 — 공개 데이터에 금액 필드가 없는 항목들.
       ponytail: 전국 단일값 휴리스틱. 3층(예측 vs 실측) 오차가 쌓이면
       지역·숙소유형별 계수로 올린다. 지금 지역별 표를 만들면 근거가 없다. */
    static final long EXTRA_PERSON_PER_NIGHT = 20_000;   // 정원 초과 1인 1박
    static final long BARBECUE               = 30_000;   // 1회
    static final long LATE_CHECKOUT          = 10_000;   // 1회
    static final long PET_PER_NIGHT          = 20_000;   // 1박

    /** 추정 항목이 흔들릴 수 있는 폭. 구간 표기의 근거이자 화면에 적는 값. */
    static final double ESTIMATE_BAND = 0.20;

    private static final ObjectMapper OM = new ObjectMapper();

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
     */
    public record Input(
            LodgingRate rate,
            int nights,
            int people,
            int baseCount,
            boolean barbecue,
            boolean barbecueAvailable,
            boolean lateCheckout,
            boolean pet
    ) {}

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

        int confidence = total == 0 ? 0 : (int) Math.round(confirmed * 100.0 / total);
        long band = Math.round(estimated * ESTIMATE_BAND);

        String note = total == 0
                ? "공개된 요금 자료를 찾지 못했습니다."
                : "신뢰도 = 확정 " + won(confirmed) + " ÷ 총액 " + won(total)
                  + " · 구간은 추정 항목 ±" + (int) (ESTIMATE_BAND * 100) + "%";

        return new BudgetEstimate(items, total, total - band, total + band, confidence, note);
    }

    /** 객실 기본료 — 확정의 유일한 출처. 공개 요금이 없으면 지역 평균 추정으로 내려간다. */
    private static Item roomFee(Input in) {
        LodgingRate rate = in.rate();
        if (rate == null || rate.amount() <= 0) {
            return Item.none("객실 기본료", "공개 요금 없음");
        }
        int nights = Math.max(1, in.nights());
        long amount = rate.amount() * nights;
        String basis = rate.basis() + " · " + rate.season() + " " + nights + "박";
        return rate.estimated()
                ? Item.estimated("객실 기본료", amount, basis)
                : Item.confirmed("객실 기본료", amount, basis);
    }

    /**
     * 인원 추가 — 초과 인원 수는 공개된 기준인원에서 나오지만 1인당 금액은 공개되지 않는다.
     * 그래서 항목 전체는 추정이고, 어디까지가 공개 데이터인지를 근거 문구에 적는다.
     */
    private static Item extraPeople(Input in) {
        if (in.baseCount() <= 0) return Item.none("인원 추가", "기준인원 정보 없음");

        int over = in.people() - in.baseCount();
        if (over <= 0) return Item.none("인원 추가", "기준인원 " + in.baseCount() + "명 이내");

        long amount = (long) over * EXTRA_PERSON_PER_NIGHT * Math.max(1, in.nights());
        return Item.estimated("인원 추가 " + over, amount,
                "기준인원 " + in.baseCount() + "명 초과 · 1인 " + won(EXTRA_PERSON_PER_NIGHT) + " 추정");
    }

    /** 바베큐 — 공개 데이터는 「가능한지」만 알려 준다. 금액은 끝까지 추정으로 남는다. */
    private static Item barbecue(Input in) {
        if (!in.barbecue()) return Item.none("바베큐", "선택 안 함");
        return Item.estimated("바베큐", BARBECUE,
                in.barbecueAvailable() ? "숙소 제공 확인 · 금액은 추정" : "지역 평균 추정");
    }

    private static Item lateCheckout(Input in) {
        if (!in.lateCheckout()) return Item.none("레이트 체크아웃", "선택 안 함");
        return Item.estimated("레이트 체크아웃", LATE_CHECKOUT, "일반 관행 추정");
    }

    private static Item pet(Input in) {
        if (!in.pet()) return Item.none("반려동물", "동반 없음");
        return Item.estimated("반려동물", PET_PER_NIGHT * Math.max(1, in.nights()), "동반 추가요금 추정");
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

        Spot stay = findStay(plan);
        if (stay == null) {
            return new Input(null, nights, people, 0, barbecue, false, lateCheckout, pet);
        }

        // 관광공사 응답은 로컬 DB 에 저장하지 않는다 — 요청마다 실시간 호출이다.
        LodgingRate rate = lodgingRateService.nightly(stay.id(), stay.areaCode(), plan.getStartDate());
        return new Input(rate, nights, people,
                lodgingRateService.baseCount(stay.id()),
                barbecue,
                barbecue && lodgingRateService.barbecueAvailable(stay.id()),
                lateCheckout, pet);
    }

    /** 숙박 일수. 당일치기면 1박으로 본다(요금 항목이 0이 되면 화면이 빈다). */
    static int nights(TravelPlan plan) {
        if (plan.getStartDate() == null || plan.getEndDate() == null) return 1;
        long n = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate());
        return (int) Math.max(1, n);
    }

    /** 동선의 숙소 이름으로 관광공사 숙박(32) 을 찾는다. 못 찾으면 null 이고 항목은 「없음」이 된다. */
    private Spot findStay(TravelPlan plan) {
        String name = stayName(plan.getRouteJson());
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
        if (routeJson == null || routeJson.isBlank()) return null;
        try {
            for (JsonNode day : OM.readTree(routeJson)) {
                for (JsonNode place : day.path("places")) {
                    if ("stay".equals(place.path("type").asText())) {
                        String name = place.path("name").asText("");
                        if (!name.isBlank()) return name;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[budget] routeJson 파싱 실패: {}", e.getMessage());
        }
        return null;
    }
}
