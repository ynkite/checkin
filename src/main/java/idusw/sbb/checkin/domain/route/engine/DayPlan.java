package idusw.sbb.checkin.domain.route.engine;

import java.time.LocalTime;
import java.util.List;

/**
 * 하루치 슬롯을 전부 이어 붙인 결과 ({@link DayPlanner} 의 산출물).
 *
 * @param dayIndex   0부터 시작하는 일자 번호
 * @param slotPlans  슬롯 순서대로, 각 슬롯의 최적 조합
 * @param returnTime 마지막 슬롯에서 숙소(또는 귀가 지점)로 복귀를 마친 시각
 */
public record DayPlan(int dayIndex, List<SlotPlan> slotPlans, LocalTime returnTime) {

    public DayPlan {
        if (dayIndex < 0) {
            throw new IllegalArgumentException("dayIndex must not be negative");
        }
        if (slotPlans == null) {
            throw new IllegalArgumentException("slotPlans must not be null");
        }
        if (returnTime == null) {
            throw new IllegalArgumentException("returnTime must not be null");
        }
        slotPlans = List.copyOf(slotPlans);
    }

    public int totalVisitCount() {
        return slotPlans.stream().mapToInt(SlotPlan::visitCount).sum();
    }
}
