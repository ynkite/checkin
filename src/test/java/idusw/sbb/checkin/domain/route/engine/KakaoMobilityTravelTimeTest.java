package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoMobilityTravelTimeTest {

    @Test
    void 아직_미구현이라_호출하면_예외를_던진다() {
        TravelTimeProvider provider = new KakaoMobilityTravelTime();
        GeoPoint a = new GeoPoint(35.0, 129.0);
        GeoPoint b = new GeoPoint(35.1, 129.1);

        assertThatThrownBy(() -> provider.estimate(a, b))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
