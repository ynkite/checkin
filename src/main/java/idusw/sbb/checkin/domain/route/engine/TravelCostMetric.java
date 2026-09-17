package idusw.sbb.checkin.domain.route.engine;

import java.util.function.ToDoubleBiFunction;

/**
 * 이동비용 함수를 만드는 유일한 자리. 같은 식이 여러 곳에 복사되면 한쪽만 고쳐졌을 때
 * 최적화가 쓴 값과 화면에 찍히는 값이 조용히 갈라진다 — 그래서 하나로 모은다.
 *
 * <p>지금은 Haversine 거리를 평균 속도로 나눈 "분" 이지만, {@code TravelTimeProvider} 를 끼우면
 * 이 클래스만 바뀐다. 쓰는 쪽 계산식은 그대로다 (설계 결정 2·5-5).
 */
public final class TravelCostMetric {

    /** 과소추정이 과대추정보다 안전하다 — 일정이 터지는 쪽이 아니라 여유가 생기는 쪽 (결정 7-4). */
    public static final double DEFAULT_AVERAGE_SPEED_KMH = 30.0;

    private TravelCostMetric() {
    }

    public static ToDoubleBiFunction<GeoPoint, GeoPoint> haversineMinutes(double averageSpeedKmh) {
        if (averageSpeedKmh <= 0) {
            throw new IllegalArgumentException("averageSpeedKmh must be positive");
        }
        return (from, to) -> Haversine.distanceKm(from, to) / averageSpeedKmh * 60.0;
    }

    public static ToDoubleBiFunction<GeoPoint, GeoPoint> haversineDefault() {
        return haversineMinutes(DEFAULT_AVERAGE_SPEED_KMH);
    }
}
