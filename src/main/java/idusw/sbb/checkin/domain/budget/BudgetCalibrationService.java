package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate.Calibration;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.expense.repository.ExpenseRepository;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 예산 학습 보정 — 다녀온 여행의 예측 대비 실제 지출 비율로 추정 항목에 곱할 배수를 낸다.
 *
 * 표본은 가계부의 예상 비용(isEstimated=true) 합과 실제 지출 합이 둘 다 있는 끝난 여행이다
 * (ExpenseService.budgetAccuracy 와 같은 기준). 시연 시드 여행은 지어낸 값이라 뺀다.
 *
 * 묶음은 좁은 것부터 넓은 것으로 내려간다 — 시도+숙소유형+성수기 → 시도+숙소유형 → 시도+성수기 → 시도.
 * 표본이 MIN_SAMPLES 에 못 미치면 다음 묶음으로 가고, 시도에서도 모자라면 보정하지 않는다.
 * 적은 표본으로 흔드는 것이 보정하지 않는 것보다 나쁘다.
 *
 * 배수는 여행별 (실제 ÷ 예측) 의 중앙값이다. 평균은 한 건이 튀면 같이 끌려간다.
 * 그래도 [MIN, MAX] 밖으로 나가면 자른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetCalibrationService {

    static final int MIN_SAMPLES = 3;
    static final double MIN = 0.8;
    static final double MAX = 1.3;
    private static final String DEMO_MARK = "시연";   // BudgetLoopSeedInitializer 의 제목 표기

    private final ExpenseRepository expenseRepository;

    /** 여행 한 건의 예측·실측. sido 는 AreaCode 시도명, type 은 입력 폼의 숙소 유형(없으면 null). */
    public record Sample(String sido, String type, boolean peak, long predicted, long actual) {
        double ratio() { return (double) actual / predicted; }
    }

    // ponytail: 요청마다 끝난 여행의 지출을 전부 읽는다. 표본이 수천 건이 되면 캐시를 둔다
    public Calibration calibrate(String sido, String type, boolean peak) {
        return calibrate(samples(expenseRepository.findOfFinishedTrips(LocalDate.now())), sido, type, peak);
    }

    // ── 계산 (순수 함수) ──

    static Calibration calibrate(List<Sample> samples, String sido, String type, boolean peak) {
        if (sido == null) return Calibration.none(0, "지역을 몰라 보정하지 않았습니다.");

        boolean hasType = type != null && !type.isBlank();
        Predicate<Sample> inSido = s -> sido.equals(s.sido());
        Predicate<Sample> sameType = s -> hasType && type.trim().equalsIgnoreCase(s.type());
        Predicate<Sample> samePeak = s -> s.peak() == peak;
        String season = peak ? "성수기" : "비수기";

        Map<String, Predicate<Sample>> groups = new LinkedHashMap<>();
        if (hasType) {
            groups.put(sido + "·" + type.trim() + "·" + season, inSido.and(sameType).and(samePeak));
            groups.put(sido + "·" + type.trim(), inSido.and(sameType));
        }
        groups.put(sido + "·" + season, inSido.and(samePeak));
        groups.put(sido, inSido);

        int widest = 0;
        for (Map.Entry<String, Predicate<Sample>> g : groups.entrySet()) {
            List<Sample> hit = samples.stream().filter(g.getValue()).toList();
            widest = hit.size();
            if (hit.size() >= MIN_SAMPLES) {
                double raw = median(hit.stream().mapToDouble(Sample::ratio).sorted().toArray());
                double m = round2(Math.max(MIN, Math.min(MAX, raw)));
                return new Calibration(true, m, round2(raw), hit.size(), g.getKey(),
                        "다녀온 여행 " + hit.size() + "건(" + g.getKey() + ")의 실제 ÷ 예측 중앙값 "
                                + String.format("×%.2f", raw)
                                + (m != round2(raw) ? " → " + MIN + "~" + MAX + " 범위로 잘라 " + String.format("×%.2f", m) : "")
                                + ". 추정 항목에만 곱했습니다.");
            }
        }
        return Calibration.none(widest, "다녀온 여행 표본이 " + widest + "건(" + sido + ")이라 보정하지 않았습니다. "
                + MIN_SAMPLES + "건부터 보정합니다.");
    }

    /** 가계부 지출을 여행별로 접는다. 예측·실측 중 한쪽이라도 없으면 표본이 아니다. */
    static List<Sample> samples(List<Expense> expenses) {
        Map<TravelPlan, long[]> byPlan = new LinkedHashMap<>();   // {예측, 실측}
        for (Expense e : expenses) {
            TravelPlan p = e.getPlan();
            if (p.getTitle() != null && p.getTitle().contains(DEMO_MARK)) continue;
            byPlan.computeIfAbsent(p, k -> new long[2])[e.isEstimated() ? 0 : 1] += e.getAmount();
        }
        List<Sample> out = new ArrayList<>();
        byPlan.forEach((p, sum) -> {
            AreaCode.Area area = AreaCode.find(p.getDestination());
            if (sum[0] <= 0 || sum[1] <= 0 || area == null) return;
            String type = p.getForm() != null ? p.getForm().getAccommodationType() : null;
            out.add(new Sample(area.sido(), type,
                    SeasonRules.strongest(p.getStartDate(), p.getEndDate()).peak(), sum[0], sum[1]));
        });
        return out;
    }

    private static double median(double[] sorted) {
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
