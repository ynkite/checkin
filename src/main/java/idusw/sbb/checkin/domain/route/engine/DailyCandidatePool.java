package idusw.sbb.checkin.domain.route.engine;

import java.util.List;

/**
 * 하루치 후보 풀. {@link BandSplitter} 의 출력이며, 슬롯 배정(SlotBuilder, 작업 3)의 입력이 된다.
 *
 * @param dayIndex   0부터 시작하는 일자 번호
 * @param sourceBand 이 풀이 어느 밴드에서 왔는지 (첫날=NEAR, 중간=MID, 마지막날=RETURN)
 * @param candidates 이 날의 후보 (5개 초과 가능 — 슬롯 단위 자르기는 SlotBuilder 몫)
 * @param relaxed    완화가 <b>필요했는지</b> — 이 날의 밴드가 스스로 {@code minPerDay} 를 못 채웠다는 뜻.
 *                   실제로 빌려 왔는지가 아니다. 빌릴 후보조차 없어 완화에 실패한 날도 true 다 —
 *                   그 두 경우를 구분하지 않던 동안 MID 가 0개인 날이 완화 표시 없이 지나갔다.
 */
public record DailyCandidatePool(int dayIndex, DistanceBand sourceBand, List<Candidate> candidates, boolean relaxed) {

    public DailyCandidatePool {
        if (dayIndex < 0) {
            throw new IllegalArgumentException("dayIndex must not be negative");
        }
        if (sourceBand == null) {
            throw new IllegalArgumentException("sourceBand must not be null");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        candidates = List.copyOf(candidates);
    }

    public int size() {
        return candidates.size();
    }
}
