package idusw.sbb.checkin.domain.budget.dto;

import idusw.sbb.checkin.domain.expense.dto.BudgetEstimate.Status;

import java.util.List;

/**
 * 예산 산출 결과.
 *
 * 화면이 「37만원」만 보여 주면 사용자는 그 값을 믿을지 말지 알 수 없다.
 * 항목마다 근거(basis)와 어림값인지(estimated)를 같이 보낸다. accuracy 는
 * 몇 항목이 실제 자료에서 왔는지를 말한다.
 */
public record BudgetEstimate(
        long total,
        long perPerson,
        int  people,
        int  nights,
        int  days,
        List<Item> items,
        Season season,
        List<Festival> festivals,
        Accuracy accuracy,
        List<String> notes,
        Calibration calibration
) {
    /**
     * @param category LODGING | FOOD | TRANSPORT | TOUR
     * @param basis    이 금액이 어디서 왔는지. 사람이 읽는 한 줄
     * @param status   확정 · 추정 · 해당없음
     *
     * <p>전에는 {@code boolean estimated} 였다. 그러면 <b>값이 없는 항목과 추정한 항목이
     * 화면에서 같은 얼굴</b>이 된다. 당일치기 여행의 숙박비는 「0원으로 추정」이 아니라
     * 「이 여행에 없는 항목」이다. 가계부 쪽이 이미 3등급이라 <b>그 enum 을 그대로 쓴다</b> —
     * 두 벌을 만들면 한쪽만 고쳐질 때 화면끼리 어긋난다.
     *
     * <p>{@code Status} 만 import 하면 이름이 같은 두 {@code BudgetEstimate} 가 부딪히지 않는다.
     */
    public record Item(
            String  category,
            String  label,
            long    amount,
            String  basis,
            Status  status,
            String  seasonApplied      // 성수기 배수를 걸었으면 그 이름. 안 걸었으면 null
    ) {
        public static Item confirmed(String category, String label, long amount, String basis, String season) {
            return new Item(category, label, amount, basis, Status.CONFIRMED, season);
        }
        public static Item estimated(String category, String label, long amount, String basis, String season) {
            return new Item(category, label, amount, basis, Status.ESTIMATED, season);
        }
        /** 해당없음 — 금액이 0인 게 아니라 이 여행에 그 항목이 없다는 뜻. */
        public static Item none(String category, String label, String basis) {
            return new Item(category, label, 0, basis, Status.NONE, null);
        }

        /** 화면·집계가 아직 쓰는 이름. 「없음」은 추정이 아니다. */
        public boolean estimated() { return status == Status.ESTIMATED; }
    }

    public record Season(
            String key,
            String label,
            double carMultiplier,
            double foodMultiplier,
            boolean weekendCheckIn,
            List<String> peakDays      // YYYY-MM-DD
    ) {}

    /**
     * @param level  HIGH | MEDIUM | LOW
     * @param real   실제 자료에서 온 항목 수
     * @param guessed 가정으로 메운 항목 수
     */
    public record Accuracy(String level, String label, int real, int guessed, String why) {}

    /**
     * 학습 보정 — 다녀온 여행의 실제 지출로 추정 항목에 곱한 배수.
     *
     * @param applied    보정했는지. false 면 multiplier 는 1.0 이다
     * @param multiplier 곱한 배수 (상·하한 적용 후)
     * @param raw        자르기 전 중앙값. 보정 안 했으면 1.0
     * @param samples    근거 표본 수. 보정 안 했을 때는 가장 넓은 묶음(시도)의 표본 수
     * @param group      묶음 이름 (「부산·호텔·성수기」). 보정 안 했으면 null
     */
    public record Calibration(boolean applied, double multiplier, double raw, int samples, String group, String why) {
        public static Calibration none(int samples, String why) {
            return new Calibration(false, 1.0, 1.0, samples, null, why);
        }
    }
}
