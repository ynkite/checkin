package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeoPointTest {

    private static final GeoPoint SEOUL = new GeoPoint(37.5665, 126.9780);
    private static final GeoPoint BUSAN = new GeoPoint(35.1796, 129.0756);

    @Test
    void 같은_지점의_거리는_0이다() {
        assertThat(SEOUL.distanceKmTo(SEOUL)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void 거리는_대칭이다() {
        assertThat(SEOUL.distanceKmTo(BUSAN)).isCloseTo(BUSAN.distanceKmTo(SEOUL), within(1e-9));
    }

    @Test
    void 서울_부산_직선거리는_대략_300에서_350km_사이다() {
        assertThat(SEOUL.distanceKmTo(BUSAN)).isBetween(300.0, 350.0);
    }

    @Test
    void 위도_범위를_벗어나면_예외() {
        assertThatThrownBy(() -> new GeoPoint(90.1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(-90.1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 경도_범위를_벗어나면_예외() {
        assertThatThrownBy(() -> new GeoPoint(0, 180.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, -180.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 경계값은_허용된다() {
        assertThat(new GeoPoint(90.0, 180.0)).isNotNull();
        assertThat(new GeoPoint(-90.0, -180.0)).isNotNull();
    }
}
