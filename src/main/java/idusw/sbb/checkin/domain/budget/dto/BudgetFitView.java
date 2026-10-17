package idusw.sbb.checkin.domain.budget.dto;

import java.util.List;

/**
 * 예산 맞춤 결과 — 화면이 「예산의 N%」와 「조정 전 → 후」를 그린다.
 *
 * @param before   조정 전 총액. 조정하지 않았으면 after 와 같다
 * @param after    지금 일정 총액
 * @param percent  after ÷ limit (반올림). 예산을 안 정했으면 0
 * @param changes  바꾼 내용 한 줄씩. 없으면 빈 목록
 * @param suggest  숙소 제안 한 줄 (일정은 안 바꿨다). 없으면 null
 * @param fallback 일정을 못 읽었다 — 화면은 가계부 총액으로 % 만 낸다
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
        boolean fallback
) {}
