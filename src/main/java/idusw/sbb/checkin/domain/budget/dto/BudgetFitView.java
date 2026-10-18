package idusw.sbb.checkin.domain.budget.dto;

import java.util.List;

/**
 * 예산 맞춤 결과 — 화면이 「예산의 N%」와 「조정 전 → 후」를 그린다.
 *
 * @param before   조정 전 총액. 조정하지 않았으면 after 와 같다
 * @param after    지금 일정 총액
 * @param percent  after ÷ limit (반올림). 예산을 안 정했으면 0
 * @param changes  바꾼 내용 한 줄씩. 없으면 빈 목록
 * @param suggest  넘으면 더 줄일 곳, 한참 남으면 여유와 쓸 곳 한 줄. 없으면 null
 * @param fallback 일정을 못 읽었다 — 화면은 가계부 총액으로 % 만 낸다
 * @param extra    일정 줄에 없는 숙박 추가 금액(옵션·추가 인원). 지도가 일정 합계에 더해 같은 총액을 낸다
 * @param lodgingTried 숙소 바꾸기 진단 — 근처 후보 수와 떨어진 이유. 예산에서 10% 넘게 벗어날 때만
 */
public record BudgetFitView(
        boolean hasLimit,
        long limit,
        long before,
        long after,
        int percent,
        List<String> changes,
        String suggest,
        String biggestLabel,
        long biggestAmount,
        boolean fallback,
        long extra,
        LodgingTried lodgingTried
) {
    /** found: 반경 안 숙소 수, priced: 요금을 매긴 후보 수, dropped: 이유별로 떨어진 수 */
    public record LodgingTried(String note, int found, int priced, java.util.Map<String, Integer> dropped) {}
}
