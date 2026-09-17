package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleDensityTest {

    @Test
    void 여유롭게는_food3_cafe1_tour1() {
        assertThat(ScheduleDensity.of("여유롭게").caps()).containsExactly(3, 1, 1);
    }

    /** 프론트 칩이 주는 실제 문자열. 라벨이 "빡빡하게" 라 매칭에 실패하고 보통으로 떨어진다 — 지금 동작 그대로다. */
    @Test
    void 빼곡하게는_매칭에_실패해_보통_food3_cafe1_tour2() {
        assertThat(ScheduleDensity.of("빼곡하게")).isEqualTo(ScheduleDensity.NORMAL);
        assertThat(ScheduleDensity.of("빼곡하게").caps()).containsExactly(3, 1, 2);
    }

    @Test
    void null이면_보통_food3_cafe1_tour2() {
        assertThat(ScheduleDensity.of(null).caps()).containsExactly(3, 1, 2);
    }

    @Test
    void 모르는_값도_보통으로_떨어진다() {
        assertThat(ScheduleDensity.of("DENSE").caps()).containsExactly(3, 1, 2);
        assertThat(ScheduleDensity.of("").caps()).containsExactly(3, 1, 2);
    }

    /** 지금은 도달할 수 없는 분기지만 매핑 자체는 기존 코드와 같아야 한다 (마감 후 라벨을 고치면 살아난다). */
    @Test
    void 빡빡하게_라벨이_들어오면_food3_cafe2_tour3() {
        assertThat(ScheduleDensity.of("빡빡하게").caps()).containsExactly(3, 2, 3);
    }

    @Test
    void 앞뒤_공백은_무시한다() {
        assertThat(ScheduleDensity.of("  여유롭게 ")).isEqualTo(ScheduleDensity.RELAXED);
    }

    @Test
    void 카테고리별_접근자도_같은_값을_준다() {
        ScheduleDensity relaxed = ScheduleDensity.RELAXED;
        assertThat(relaxed.foodCap()).isEqualTo(3);
        assertThat(relaxed.cafeCap()).isEqualTo(1);
        assertThat(relaxed.tourCap()).isEqualTo(1);
    }
}
