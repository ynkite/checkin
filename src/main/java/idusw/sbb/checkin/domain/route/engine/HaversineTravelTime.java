package idusw.sbb.checkin.domain.route.engine;

/**
 * 키·API 호출 없이 즉시 계산되는 개발용 {@link TravelTimeProvider}. Haversine 거리를
 * 평균 속도로 나눠 분 단위 이동시간을 근사한다 — 지금 BandSplitter/SlotOptimizer/
 * DayPlanner의 {@code withDefaults()} 가 쓰는 것과 같은 가정(30km/h)이다.
 *
 * <p>과소추정이 과대추정보다 안전하다는 원칙(9/16~18 튜닝 목록)에 따라 도심 혼합 속도로
 * 잡은 기본값이고, 실제 도로 사정(신호·정체)은 반영하지 않는다 — {@link KakaoMobilityTravelTime}
 * 이 이 자리를 대체할 때까지의 스텁이다.
 */
public final class HaversineTravelTime implements TravelTimeProvider {

    public static final double DEFAULT_AVERAGE_SPEED_KMH = 30.0;

    private final double averageSpeedKmh;

    public HaversineTravelTime(double averageSpeedKmh) {
        if (averageSpeedKmh <= 0) {
            throw new IllegalArgumentException("averageSpeedKmh must be positive");
        }
        this.averageSpeedKmh = averageSpeedKmh;
    }

    public HaversineTravelTime() {
        this(DEFAULT_AVERAGE_SPEED_KMH);
    }

    @Override
    public double estimate(GeoPoint from, GeoPoint to) {
        return Haversine.distanceKm(from, to) / averageSpeedKmh * 60.0;
    }
}
