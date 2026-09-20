package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.budget.dto.BudgetRequest;
import idusw.sbb.checkin.domain.budget.dto.Festival;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.tour.dto.LodgingRate;
import idusw.sbb.checkin.domain.tour.service.LodgingRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 예산 산출 — 숙박 · 식비 · 이동 · 입장료.
 *
 * 성수기를 어디에 곱하는가 (SeasonRules 의 주석과 같은 이야기) —
 *   숙박은 곱하지 않는다. 관광공사 요금표에서 「성수기 주말」 칸을 골라
 *   쓰므로 거기에 또 곱하면 두 번 센다. 요금표가 없어 지역 평균으로
 *   메울 때만 곱한다.
 *   렌터카는 곱한다. 제주 8월 렌터카는 실제로 두 배 가까이 간다.
 *   식비는 조금 곱한다. 대중교통·유류비·입장료는 안 곱한다.
 *
 * 단가는 코드에 박지 않고 설정에서 받는다. 물가가 바뀌면 값만 바꾼다.
 * 기본값은 2026년 국내 여행 평균에 대한 우리 가정이고, 결과의 basis 에
 * 「가정」이라고 적어 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetEstimateService {

    @Value("${budget.meal-per-person:12000}")      private long mealPerPerson;      // 1끼
    @Value("${budget.meals-per-day:3}")            private int  mealsPerDay;
    @Value("${budget.admission-per-place:8000}")   private long admissionPerPlace;  // 1인 1곳
    @Value("${budget.lodging-fallback:80000}")     private long lodgingFallback;    // 1박 (요금표 없을 때)
    @Value("${budget.rental-per-day:70000}")       private long rentalPerDay;
    @Value("${budget.fuel-per-km:130}")            private long fuelPerKm;
    @Value("${budget.transit-per-leg:1500}")       private long transitPerLeg;
    @Value("${budget.km-per-day-assumed:60}")      private int  kmPerDayAssumed;    // 거리 모를 때

    private final LodgingRateService lodgingRateService;
    private final FestivalService festivalService;

    public BudgetEstimate estimate(BudgetRequest req) {
        int people = req.peopleOr(2);
        int nights = req.nights();
        int days = req.days();
        String transport = req.transportOr("CAR");
        LocalDate from = req.from() != null ? req.from() : LocalDate.now();
        LocalDate to = req.to() != null ? req.to() : from;

        AreaCode.Area area = AreaCode.find(req.region());
        SeasonRules.Season season = SeasonRules.strongest(from, to);
        boolean weekend = SeasonRules.weekendCheckIn(from);

        List<BudgetEstimate.Item> items = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int real = 0, guessed = 0;

        /* ── 숙박 ─────────────────────────────────────────── */
        if (nights > 0) {
            LodgingRate rate = null;
            if (req.lodgingContentId() != null && !req.lodgingContentId().isBlank()) {
                try {
                    rate = lodgingRateService.nightly(req.lodgingContentId(),
                            area != null ? AreaCode.tourAreaCode(area) : null, from);
                } catch (Exception e) {
                    log.warn("[budget] 숙박 요금 조회 실패: {}", e.getMessage());
                }
            }
            if (rate != null && rate.amount() > 0) {
                /* 요금표에서 온 값이다. 성수기를 또 곱하지 않는다 */
                long amount = rate.amount() * nights;
                items.add(rate.estimated()
                        ? BudgetEstimate.Item.estimated("LODGING", nights + "박 숙박", amount,
                                rate.basis() + " · " + rate.season(), null)
                        : BudgetEstimate.Item.confirmed("LODGING", nights + "박 숙박", amount,
                                rate.basis() + " · " + rate.season(), null));
                if (rate.estimated()) guessed++; else real++;
            } else {
                double m = seasonLodgingMultiplier(season, weekend);
                long amount = Math.round(lodgingFallback * m) * nights * rooms(people);
                items.add(BudgetEstimate.Item.estimated("LODGING", nights + "박 숙박", amount,
                        "요금표가 없어 1박 " + won(lodgingFallback) + " 가정 · 방 " + rooms(people) + "개"
                                + (m > 1.0 ? " · " + season.label()
                                    + (weekend ? " 주말" : "") + " " + x(m) : ""),
                        m > 1.0 ? season.label() : null));
                guessed++;
                if (req.lodgingContentId() == null || req.lodgingContentId().isBlank()) {
                    notes.add("숙소를 고르면 관광공사 공개 요금표로 숙박비가 확정됩니다.");
                }
            }
        } else {
            /* 빼지 않고 「해당없음」으로 남긴다. 빠진 것과 없는 것은 다르다 —
               당일치기의 숙박비는 「0원으로 추정」이 아니라 「이 여행에 없는 항목」이다 */
            items.add(BudgetEstimate.Item.none("LODGING", "숙박", "당일치기라 숙박이 없습니다"));
        }

        /* ── 식비 ─────────────────────────────────────────── */
        long meal = Math.round(mealPerPerson * season.foodMultiplier());
        long foodTotal = meal * mealsPerDay * days * people;
        items.add(BudgetEstimate.Item.estimated("FOOD",
                days + "일 식비", foodTotal,
                "1인 1끼 " + won(meal) + " · 하루 " + mealsPerDay + "끼 가정"
                        + (season.foodMultiplier() > 1.0
                            ? " · " + season.label() + " " + x(season.foodMultiplier()) : ""),
                 season.foodMultiplier() > 1.0 ? season.label() : null));
        guessed++;

        /* ── 이동 ─────────────────────────────────────────── */
        boolean meters = req.totalMeters() != null && req.totalMeters() > 0;
        int km = meters ? (int) Math.round(req.totalMeters() / 1000.0) : kmPerDayAssumed * days;

        if ("TRANSIT".equals(transport)) {
            if (req.transitFare() != null && req.transitFare() > 0) {
                long amount = (long) req.transitFare() * people;
                items.add(BudgetEstimate.Item.confirmed("TRANSPORT", "대중교통", amount,
                        "티맵 대중교통 요금 합 · " + people + "명", null));
                real++;
            } else {
                int legs = Math.max(1, (req.stops() == null ? days : req.stops().size()));
                long amount = transitPerLeg * legs * people;
                items.add(BudgetEstimate.Item.estimated("TRANSPORT", "대중교통", amount,
                        "구간 " + legs + "번 × " + won(transitPerLeg) + " 가정 · " + people + "명",
                        null));
                guessed++;
                notes.add("출발지를 넣으면 티맵으로 실제 요금을 계산합니다.");
            }
        } else if ("RENTAL".equals(transport)) {
            long car = Math.round(rentalPerDay * season.carMultiplier()) * days;
            long fuel = (long) km * fuelPerKm;
            items.add(BudgetEstimate.Item.estimated("TRANSPORT", "렌터카 " + days + "일", car,
                    "1일 " + won(rentalPerDay) + " 가정"
                            + (season.carMultiplier() > 1.0
                                ? " · " + season.label() + " " + x(season.carMultiplier()) : ""),
                    season.carMultiplier() > 1.0 ? season.label() : null));
            items.add(meters
                    ? BudgetEstimate.Item.confirmed("TRANSPORT", "유류비", fuel,
                            km + "km × " + won(fuelPerKm) + "/km (티맵 실측 거리)", null)
                    : BudgetEstimate.Item.estimated("TRANSPORT", "유류비", fuel,
                            km + "km × " + won(fuelPerKm) + "/km (하루 " + kmPerDayAssumed + "km 가정)", null));
            guessed++;
            if (meters) real++; else guessed++;
        } else {
            long fuel = (long) km * fuelPerKm;
            items.add(meters
                    ? BudgetEstimate.Item.confirmed("TRANSPORT", "유류비", fuel,
                            km + "km × " + won(fuelPerKm) + "/km (티맵 실측 거리)", null)
                    : BudgetEstimate.Item.estimated("TRANSPORT", "유류비", fuel,
                            km + "km × " + won(fuelPerKm) + "/km (하루 " + kmPerDayAssumed + "km 가정)", null));
            if (meters) real++; else guessed++;
            if (!meters) notes.add("출발지를 넣으면 티맵 실측 거리로 유류비를 다시 냅니다.");
        }

        /* ── 입장료 ───────────────────────────────────────── */
        int places = req.stops() == null ? 0 : req.stops().size();
        if (places > 0) {
            long amount = admissionPerPlace * places * people;
            items.add(BudgetEstimate.Item.estimated("TOUR", "입장료 " + places + "곳", amount,
                    "1인 1곳 " + won(admissionPerPlace) + " 가정 · 무료인 곳은 빠집니다", null));
            guessed++;
        } else {
            items.add(BudgetEstimate.Item.none("TOUR", "입장료", "들를 곳이 아직 없습니다"));
        }

        /* ── 축제 ─────────────────────────────────────────── */
        List<Festival> festivals = List.of();
        String tourArea = AreaCode.tourAreaCode(area);
        if (tourArea != null) {
            try {
                festivals = festivalService.during(tourArea, from, to);
            } catch (Exception e) {
                log.warn("[budget] 축제 조회 실패: {}", e.getMessage());
            }
        }
        /* 축제는 금액에 넣지 않는다 (팀 결정). 관광공사 searchFestival2 에 2026년 축제가
           거의 안 올라와 있어서, 계수를 넣어도 대부분 동작하지 않고 엔진만 복잡해진다.
           그렇다고 아무 말도 안 하면 「축제를 반영한 값」으로 읽힌다. 문구로만 적는다.
           ★여기에 배수를 곱하지 말 것 — 곱하려면 팀 결정을 먼저 뒤집어야 한다 */
        if (!festivals.isEmpty()) {
            notes.add("여행 기간에 " + festivals.get(0).title()
                    + (festivals.size() > 1 ? " 등 축제 " + festivals.size() + "개" : "")
                    + "가 열립니다. 숙소가 빨리 찹니다.");
            notes.add("축제는 위 금액에 넣지 않았습니다. 숙소·교통이 더 들 수 있습니다.");
        }
        if (area == null) {
            notes.add("지역을 못 알아봐서 숙박·축제는 지역 자료 없이 냈습니다.");
        }

        long total = items.stream().mapToLong(BudgetEstimate.Item::amount).sum();

        return new BudgetEstimate(total,
                people > 0 ? total / people : total,
                people, nights, days, items,
                new BudgetEstimate.Season(season.key(), season.label(),
                        season.carMultiplier(), season.foodMultiplier(), weekend,
                        SeasonRules.peakDays(from, to).stream().map(LocalDate::toString).toList()),
                festivals,
                accuracy(real, guessed),
                notes);
    }

    /* 요금표가 없어 지역 평균으로 메울 때만 성수기를 곱한다.
       주말은 성수기와 곱해지지 않고 더해진다 — 둘을 곱하면 2.6배가 되어
       실제 숙박비보다 커진다. */
    private double seasonLodgingMultiplier(SeasonRules.Season s, boolean weekend) {
        double m = 1.0;
        if (s.peak()) m += (s.carMultiplier() - 1.0) * 0.45;   // 렌터카만큼 오르지는 않는다
        if (weekend) m += 0.30;
        return Math.min(2.0, m);
    }

    /** 방 개수 — 2인 1실. 3명이면 2실이다. */
    private int rooms(int people) {
        return Math.max(1, (people + 1) / 2);
    }

    private BudgetEstimate.Accuracy accuracy(int real, int guessed) {
        int all = real + guessed;
        if (all == 0) {
            return new BudgetEstimate.Accuracy("LOW", "낮음", 0, 0, "계산할 항목이 없습니다.");
        }
        double ratio = (double) real / all;
        if (ratio >= 0.5) {
            return new BudgetEstimate.Accuracy("HIGH", "높음", real, guessed,
                    "항목 " + all + "개 중 " + real + "개가 실제 자료(공개 요금표·티맵 실측)에서 왔습니다.");
        }
        if (ratio > 0) {
            return new BudgetEstimate.Accuracy("MEDIUM", "보통", real, guessed,
                    "항목 " + all + "개 중 " + real + "개만 실제 자료입니다. "
                            + "숙소와 출발지를 넣으면 올라갑니다.");
        }
        return new BudgetEstimate.Accuracy("LOW", "낮음", 0, guessed,
                "전부 평균 가정으로 낸 값입니다. 숙소와 출발지를 넣으면 실제 자료로 바뀝니다.");
    }

    private static String won(long v) {
        return String.format("%,d원", v);
    }

    private static String x(double m) {
        return String.format("×%.2f", m);
    }
}
