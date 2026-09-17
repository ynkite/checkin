package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class HaversineTravelTimeTest {

    private static final GeoPoint SEOUL = new GeoPoint(37.5665, 126.9780);
    private static final GeoPoint BUSAN = new GeoPoint(35.1796, 129.0756);

    @Test
    void 거리를_평균속도로_나눈_분_단위_값을_준다() {
        TravelTimeProvider provider = new HaversineTravelTime(60.0); // 시속 60km → km 가 곧 분
        double expectedMinutes = Haversine.distanceKm(SEOUL, BUSAN) / 60.0 * 60.0;

        assertThat(provider.estimate(SEOUL, BUSAN)).isCloseTo(expectedMinutes, within(1e-9));
    }

    @Test
    void 기본_생성자는_시속_30km를_쓴다() {
        HaversineTravelTime provider = new HaversineTravelTime();
        double expected = Haversine.distanceKm(SEOUL, BUSAN) / 30.0 * 60.0;

        assertThat(provider.estimate(SEOUL, BUSAN)).isCloseTo(expected, within(1e-9));
        assertThat(HaversineTravelTime.DEFAULT_AVERAGE_SPEED_KMH).isEqualTo(30.0);
    }

    @Test
    void 같은_지점이면_0분이다() {
        TravelTimeProvider provider = new HaversineTravelTime();
        assertThat(provider.estimate(SEOUL, SEOUL)).isZero();
    }

    @Test
    void 평균속도가_0이하면_예외() {
        assertThatThrownBy(() -> new HaversineTravelTime(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HaversineTravelTime(-10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
