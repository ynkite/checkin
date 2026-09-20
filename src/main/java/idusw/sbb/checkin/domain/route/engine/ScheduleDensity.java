package idusw.sbb.checkin.domain.route.engine;

/**
 * 일정 밀도별 하루 카테고리 상한 [food, cafe, tour]. {@code AiRouteService.postProcessRoute} 가
 * 이 상한으로 초과분을 삭제한다 — 엔진도 같은 값을 예산으로 받아야 뒤에서 잘리지 않는다 (결정 11-(1)).
 *
 * <p><b>라벨은 화면이 보내는 말과 같아야 한다.</b> 전에는 여기가 "빡빡하게" 인데 프론트 칩은
 * "빼곡하게" 라서, 빼곡을 고른 플랜이 전부 {@link #NORMAL} 로 떨어졌다 — 관광지가 3곳이 아니라
 * 2곳이 됐다. 「빡빡하게」를 보내는 화면은 어디에도 없었고 그렇게 저장된 데이터도 없었다.
 * 코드만 오지 않는 값을 기다리고 있었다.
 *
 * <p>같은 말이 두 개로 갈린 것이라 <b>화면 쪽 말(빼곡하게)로 합쳤다.</b> 새 말을 만들지 않는다 —
 * 사람이 쓰는 말이 기준이다.
 *
 * <p>{@code of} 는 enum 이름({@code RELAXED} 등)도 받는다. 실제로 그렇게 저장된 행이 있었고,
 * 그것도 조용히 {@link #NORMAL} 로 떨어지고 있었다.
 */
public enum ScheduleDensity {

    PACKED("빼곡하게", 3, 2, 3),
    RELAXED("여유롭게", 3, 1, 1),
    /** 라벨 없음 — 아는 라벨이 아니면 전부 여기로 떨어진다. */
    NORMAL(null, 3, 1, 2);

    private final String label;
    private final int foodCap;
    private final int cafeCap;
    private final int tourCap;

    ScheduleDensity(String label, int foodCap, int cafeCap, int tourCap) {
        this.label = label;
        this.foodCap = foodCap;
        this.cafeCap = cafeCap;
        this.tourCap = tourCap;
    }

    /** {@code PlanInputForm.scheduleDensity} 문자열 → 밀도. null·공백·모르는 값은 {@link #NORMAL}. */
    public static ScheduleDensity of(String label) {
        if (label == null) {
            return NORMAL;
        }
        String trimmed = label.trim();
        for (ScheduleDensity density : values()) {
            if (trimmed.equals(density.label) || trimmed.equals(density.name())) {
                return density;
            }
        }
        return NORMAL;
    }

    public int foodCap() {
        return foodCap;
    }

    public int cafeCap() {
        return cafeCap;
    }

    public int tourCap() {
        return tourCap;
    }

    /** {@code postProcessRoute} 가 쓰는 배열 형태 [food, cafe, tour]. */
    public int[] caps() {
        return new int[]{foodCap, cafeCap, tourCap};
    }
}
