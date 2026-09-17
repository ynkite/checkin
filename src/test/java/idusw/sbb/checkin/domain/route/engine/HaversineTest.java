package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineTest {

    @Test
    void 같은_지점은_0km() {
        GeoPoint p = new GeoPoint(35.1595, 129.0756); // 부산
        assertThat(Haversine.distanceKm(p, p)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void 위도만_1도_차이면_대략_111km() {
        GeoPoint a = new GeoPoint(35.0, 129.0);
        GeoPoint b = new GeoPoint(36.0, 129.0);
        assertThat(Haversine.distanceKm(a, b)).isCloseTo(111.19, within(1.0));
    }

    @Test
    void 거리는_음수가_될_수_없다() {
        GeoPoint a = new GeoPoint(37.0, 127.0);
        GeoPoint b = new GeoPoint(35.0, 129.0);
        assertThat(Haversine.distanceKm(a, b)).isGreaterThan(0.0);
    }
}
