package idusw.sbb.checkin.domain.expense.dto;

import java.util.List;

/**
 * 숙박비 예측 결과 — 항목별 근거 공개 (예산 엔진 2층).
 *
 * 총액을 단일 숫자로 주지 않는다. 항목마다 확정·추정·해당없음을 붙이고,
 * 신뢰도는 「확정 항목이 총액에서 차지하는 비중」이라는 계산식으로만 낸다.
 * 근거 없는 정확도 수치는 화면에 올리지 않는다.
 *
 * @param items      항목 목록. 해당없음도 0원으로 남긴다 — 빠진 게 아니라 없는 것이다
 * @param total      항목 합계
 * @param low        예측 구간 하단 (추정 항목 ±20%)
 * @param high       예측 구간 상단
 * @param confidence 신뢰도 % = 확정 금액 ÷ 총액
 * @param note       신뢰도 산출식 문구. 화면에 그대로 보여 준다
 */
public record BudgetEstimate(
        List<Item> items,
        long total,
        long low,
        long high,
        int confidence,
        String note
) {
    /** 항목 3상태. 추정을 확정으로 위장하지 않는다. */
    public enum Status { CONFIRMED, ESTIMATED, NONE }

    /**
     * @param label  항목명 (객실 기본료 · 인원 추가 …)
     * @param amount 금액
     * @param status 확정 / 추정 / 해당없음
     * @param basis  근거 문구. 항목과 같은 줄로 화면에 나간다
     */
    public record Item(String label, long amount, Status status, String basis) {
        public static Item confirmed(String label, long amount, String basis) {
            return new Item(label, amount, Status.CONFIRMED, basis);
        }
        public static Item estimated(String label, long amount, String basis) {
            return new Item(label, amount, Status.ESTIMATED, basis);
        }
        /** 해당없음 — 금액이 0인 게 아니라 이 여행에 그 항목이 없다는 뜻. */
        public static Item none(String label, String basis) {
            return new Item(label, 0, Status.NONE, basis);
        }
    }
}
