package idusw.sbb.checkin.domain.budget.dto;

/**
 * 바뀐 동선이 사용자가 정한 예산을 넘는지 판단한 결과.
 *
 * 저장 로직(AiRouteService.saveAiRouteToDb)은 건드리지 않는다.
 * 동선 담당이 저장 후 이 판단기를 불러 응답에 한 칸을 붙이고,
 * 화면은 강요 없이 「이대로 / 더 싼 곳으로」 두 갈래를 준다.
 */
public record BudgetLimitCheck(
        boolean hasLimit,      // 사용자가 예산을 정했는가 (안 정했으면 초과 판단 자체가 없다)
        boolean over,          // 예산 초과 여부
        long    total,         // 바뀐 동선 총액
        long    limit,         // 사용자가 정한 예산
        long    overBy,        // 초과액 (초과 아니면 0)
        String  biggestLabel,  // 총액에서 가장 큰 항목 (줄일 후보). 없으면 null
        long    biggestAmount  // 그 항목 금액
) {}
