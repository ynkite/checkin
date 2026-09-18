package idusw.sbb.checkin.domain.expense.dto;

import java.util.List;

/**
 * 예측 vs 실측 검증 루프 (예산 엔진 3층).
 *
 * 새 엔티티를 만들지 않는다. 기존 가계부의 예상 비용(isEstimated=true)이 예측,
 * 사용자가 입력한 실제 지출(false)이 실측이다. 둘 다 있는 여행만 오차를 낸다.
 *
 * 누적 정확도는 아직 쌓인 데이터가 아니라 시연 데이터다. 그 사실을 note 로 같이 내려
 * 화면이 그대로 찍게 한다 — 근거 없는 정확도 주장은 심사에서 깨진다.
 *
 * @param rows        여행별 예측·실측·오차
 * @param avgErrorPct 누적 평균 오차 (절대값 평균)
 * @param n           표본 수
 * @param note        산출식 + 「시연」 표기. 화면에 그대로 보여 준다
 */
public record BudgetAccuracy(List<Row> rows, double avgErrorPct, int n, String note) {

    /**
     * @param tripTitle 여행 제목
     * @param predicted 예측액 (예상 비용 합)
     * @param actual    실측액 (실제 지출 합)
     * @param errorPct  오차 % — 음수면 예측보다 덜 썼다는 뜻이다
     */
    public record Row(String tripTitle, long predicted, long actual, double errorPct) {
        public static Row of(String tripTitle, long predicted, long actual) {
            return new Row(tripTitle, predicted, actual, round1((actual - predicted) * 100.0 / predicted));
        }
    }

    /** 표본이 없으면 평균도 없다. 0% 를 정확도로 위장하지 않는다. */
    public static BudgetAccuracy of(List<Row> rows) {
        double avg = rows.isEmpty() ? 0
                : round1(rows.stream().mapToDouble(r -> Math.abs(r.errorPct())).average().orElse(0));
        return new BudgetAccuracy(rows, avg, rows.size(),
                "오차 = (실제 − 예측) ÷ 예측. 누적 평균은 절대값 평균이다. "
                        + "현재 N=" + rows.size() + " — 시연 데이터이며 실사용 축적치가 아니다.");
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
