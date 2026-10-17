package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.budget.dto.BudgetLimitCheck;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 바뀐 동선이 예산 한도를 넘는지 판단만 한다 (작업지시 3번).
 *
 * 저장 함수(AiRouteService.saveAiRouteToDb)는 건드리지 않는다 — 동선 담당이 계속 고치는
 * 파일이라 충돌을 피한다. 동선 쪽에서 저장 후 이 판단기를 불러 응답에 한 칸을 붙인다.
 *
 *   입력  바뀐 동선의 산출(BudgetEstimate), 사용자가 정한 예산(원)
 *   출력  초과 여부 · 얼마 초과 · 어느 항목이 크게 먹었는지
 */
@Service
public class BudgetLimitService {

    /**
     * @param estimate 바뀐 동선의 예산 산출 (BudgetEstimateService.estimate 결과)
     * @param limitWon 사용자가 정한 예산. 0 이하면 「예산 안 정함」으로 본다
     */
    public BudgetLimitCheck check(BudgetEstimate estimate, long limitWon) {
        return estimate == null ? check(0, List.of(), limitWon) : check(estimate.total(), estimate.items(), limitWon);
    }

    /** 산출 객체 없이 총액·항목만 있을 때 (예산 맞춤이 일정에서 바로 부른다). */
    public BudgetLimitCheck check(long total, List<BudgetEstimate.Item> items, long limitWon) {
        // 총액에서 가장 큰 항목 — 줄일 후보로 화면이 제시한다
        String biggestLabel = null;
        long biggestAmount = 0;
        for (BudgetEstimate.Item it : items == null ? List.<BudgetEstimate.Item>of() : items) {
            if (it.amount() > biggestAmount) {
                biggestAmount = it.amount();
                biggestLabel = it.label();
            }
        }

        if (limitWon <= 0) {
            // 예산을 안 정했으면 초과 판단이 성립하지 않는다. 0 으로 「없음」을 위장하지 않는다.
            return new BudgetLimitCheck(false, false, total, 0, 0, biggestLabel, biggestAmount);
        }

        long overBy = Math.max(0, total - limitWon);
        return new BudgetLimitCheck(true, overBy > 0, total, limitWon, overBy, biggestLabel, biggestAmount);
    }
}
