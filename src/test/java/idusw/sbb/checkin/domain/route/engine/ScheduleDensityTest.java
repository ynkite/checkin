package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleDensityTest {

    @Test
    void 여유롭게는_food3_cafe1_tour1() {
        assertThat(ScheduleDensity.of("여유롭게").caps()).containsExactly(3, 1, 1);
    }

    /**
     * 프론트 칩이 주는 실제 문자열. 전에는 라벨이 "빡빡하게" 라 매칭에 실패하고 보통으로
     * 떨어졌다 — 빼곡을 고른 사람이 관광지를 3곳이 아니라 2곳 받았다.
     */
    @Test
    void 빼곡하게는_food3_cafe2_tour3() {
        assertThat(ScheduleDensity.of("빼곡하게")).isEqualTo(ScheduleDensity.PACKED);
        assertThat(ScheduleDensity.of("빼곡하게").caps()).containsExactly(3, 2, 3);
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

    /** 화면이 쓰는 말로 합쳤다. 「빡빡하게」를 보내는 화면은 없었고 그렇게 저장된 데이터도 없었다. */
    @Test
    void 안_쓰는_말은_보통으로_떨어진다() {
        assertThat(ScheduleDensity.of("빡빡하게")).isEqualTo(ScheduleDensity.NORMAL);
    }

    /** DB 에 enum 이름이 그대로 들어간 행이 있었다. 그것도 조용히 보통으로 떨어지고 있었다. */
    @Test
    void enum_이름으로도_찾는다() {
        assertThat(ScheduleDensity.of("RELAXED")).isEqualTo(ScheduleDensity.RELAXED);
        assertThat(ScheduleDensity.of("PACKED")).isEqualTo(ScheduleDensity.PACKED);
        assertThat(ScheduleDensity.of("NORMAL")).isEqualTo(ScheduleDensity.NORMAL);
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
