package idusw.sbb.checkin.domain.route.engine;

/**
 * 일정 밀도별 하루 카테고리 상한 [food, cafe, tour]. {@code AiRouteService.postProcessRoute} 가
 * 이 상한으로 초과분을 삭제한다 — 엔진도 같은 값을 예산으로 받아야 뒤에서 잘리지 않는다 (결정 11-(1)).
 *
 * <p><b>{@link #PACKED} 의 라벨은 지금 어떤 입력으로도 안 걸린다.</b> 프론트 칩은 "빼곡하게" 인데
 * 여기 라벨은 "빡빡하게" 라서, 빼곡을 고른 플랜은 {@link #NORMAL} 로 떨어진다. 지금 고치면 기존
 * 경로의 출력이 바뀌어 {@code ..\07_before_증거.md} 의 before 데이터가 무효가 된다. 팀에 공유하고
 * 마감 후에 고친다 — 그때까지 <b>이 오타는 의도적으로 보존한다.</b>
 */
public enum ScheduleDensity {

    /** 도달 불가 — 위 javadoc 참조. */
    PACKED("빡빡하게", 3, 2, 3),
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
            if (trimmed.equals(density.label)) {
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
