package idusw.sbb.checkin.domain.route.engine;

import java.util.EnumMap;
import java.util.Map;

/**
 * 하루에 카테고리별로 몇 곳까지 넣을지. {@link ScheduleDensity} 의 상한을 그대로 받아
 * {@link DayPlanner} 가 슬롯에 나눠 쓴다 (결정 11-(1)).
 *
 * <p>뒤 단계 {@code postProcessRoute} 가 같은 상한으로 초과분을 삭제하기 때문에, 엔진이 이걸
 * 지키면 삭제가 한 건도 안 걸린다 — 뒤 단계를 고치지 않고 맞추는 쪽이다.
 */
public record DailyCategoryBudget(int food, int cafe, int tour) {

    public DailyCategoryBudget {
        if (food < 0 || cafe < 0 || tour < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
    }

    public static DailyCategoryBudget of(ScheduleDensity density) {
        if (density == null) {
            throw new IllegalArgumentException("density must not be null");
        }
        return new DailyCategoryBudget(density.foodCap(), density.cafeCap(), density.tourCap());
    }

    /** 밀도를 모르는 호출(기존 테스트·단독 사용)에서 쓰는 무제한 예산 — 시간이 알아서 자른다. */
    public static DailyCategoryBudget unlimited() {
        return new DailyCategoryBudget(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public boolean isUnlimited() {
        return food == Integer.MAX_VALUE && cafe == Integer.MAX_VALUE && tour == Integer.MAX_VALUE;
    }

    /** 활동 슬롯(관광·카페)에 쓸 수 있는 하루 총량. 식사는 슬롯 상한 1로 따로 묶인다. */
    public int activityTotal() {
        return isUnlimited() ? Integer.MAX_VALUE : cafe + tour;
    }

    /** 하루를 돌며 깎아 쓸 잔여 예산. 원본은 그대로 둔다. */
    public Map<CandidateCategory, Integer> toRemaining() {
        Map<CandidateCategory, Integer> remaining = new EnumMap<>(CandidateCategory.class);
        remaining.put(CandidateCategory.FOOD, food);
        remaining.put(CandidateCategory.CAFE, cafe);
        remaining.put(CandidateCategory.TOUR, tour);
        return remaining;
    }
}
