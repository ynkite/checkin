package idusw.sbb.checkin.domain.route.engine;

/**
 * 자리만 만들어 둔 실제 구현. 카카오모빌리티 길찾기 API 연동이 확정되면 여기를 채운다 —
 * {@link TravelTimeProvider} 계약(두 좌표 → 분)은 이미 확정돼 있으니 호출부(엔진)는
 * 손댈 필요가 없다.
 */
public final class KakaoMobilityTravelTime implements TravelTimeProvider {

    @Override
    public double estimate(GeoPoint from, GeoPoint to) {
        throw new UnsupportedOperationException(
                "KakaoMobilityTravelTime 미구현 — 카카오모빌리티 길찾기 API 연동 전까지는 HaversineTravelTime 을 쓴다");
    }
}
