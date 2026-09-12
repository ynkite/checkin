package idusw.sbb.checkin.domain.route.engine;

import java.time.LocalTime;
import java.util.List;

/**
 * {@link SlotOptimizer} 가 슬롯 하나에 대해 고른 최적 조합·순서의 결과.
 * {@code visitOrder} 가 비어 있으면 "이 슬롯엔 아무도 안 간다" — 정상적인 결과다 (결정 8).
 *
 * @param type        어느 슬롯인지
 * @param visitOrder  방문 순서 (비어 있을 수 있다)
 * @param endPoint    마지막 방문지 위치 (방문이 없으면 시작 지점 그대로)
 * @param endTime     마지막 방문의 체류가 끝난 시각 (방문이 없으면 시작 시각 그대로)
 * @param travelCost  이 슬롯 안에서 쓴 이동비용 총합 — 직전 지점→첫 방문지 구간을 포함한다
 *                    ("도착하는 쪽이 부담한다", 결정 9-3)
 */
public record SlotPlan(SlotType type, List<Candidate> visitOrder, GeoPoint endPoint, LocalTime endTime, double travelCost) {

    public SlotPlan {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (visitOrder == null) {
            throw new IllegalArgumentException("visitOrder must not be null");
        }
        if (endPoint == null) {
            throw new IllegalArgumentException("endPoint must not be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("endTime must not be null");
        }
        if (travelCost < 0) {
            throw new IllegalArgumentException("travelCost must not be negative");
        }
        visitOrder = List.copyOf(visitOrder);
    }

    /** 아무도 방문하지 않는 결과 — 시작 지점·시각을 그대로 돌려준다. */
    public static SlotPlan empty(SlotType type, GeoPoint startPoint, LocalTime startTime) {
        return new SlotPlan(type, List.of(), startPoint, startTime, 0.0);
    }

    public int visitCount() {
        return visitOrder.size();
    }
}
