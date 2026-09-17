package idusw.sbb.checkin.domain.route.engine;

import java.util.List;

/**
 * 하루치 후보 풀. {@link BandSplitter} 의 출력이며, 슬롯 배정(SlotBuilder, 작업 3)의 입력이 된다.
 *
 * @param dayIndex   0부터 시작하는 일자 번호
 * @param sourceBand 이 풀이 어느 밴드에서 왔는지 (첫날=NEAR, 중간=MID, 마지막날=RETURN)
 * @param candidates 이 날의 후보 (5개 초과 가능 — 슬롯 단위 자르기는 SlotBuilder 몫)
 * @param relaxed    최소치를 채우려고 규칙을 완화(인접 밴드 차용 · 우회비용 임계 완화)했는지
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
