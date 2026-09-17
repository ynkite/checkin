package idusw.sbb.checkin.domain.route.engine;

import java.util.List;

/**
 * 하루 리듬 한 칸. 후보 3~5개를 담아 {@code SlotOptimizer} 의 완전탐색 대상이 된다.
 * 이 클래스는 후보 목록을 담을 뿐, 순서 최적화는 하지 않는다.
 */
public record TimeSlot(SlotType type, List<Candidate> candidates) {

    public static final int MAX_CANDIDATES = 5;

    public TimeSlot {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException(
                    "candidates must not exceed " + MAX_CANDIDATES + " but was " + candidates.size());
        }
        candidates = List.copyOf(candidates);
    }

    public boolean isMealSlot() {
        return type == SlotType.LUNCH || type == SlotType.DINNER;
    }

    public int size() {
        return candidates.size();
    }
}
