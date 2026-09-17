package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceBandTest {

    @Test
    void 세_라벨만_존재한다() {
        assertThat(DistanceBand.values())
                .containsExactly(DistanceBand.NEAR, DistanceBand.MID, DistanceBand.RETURN);
    }
}
