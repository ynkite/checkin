package idusw.sbb.checkin.domain.route.engine;

/**
 * 두 좌표 사이 실제 이동시간(분)을 준다 — 작업 4의 경계 인터페이스 중 하나.
 *
 * <p>BandSplitter/SlotBuilder/SlotOptimizer/DayPlanner가 생성자로 받는
 * {@code ToDoubleBiFunction<GeoPoint, GeoPoint>} 와 시그니처가 같다({@code estimate}
 * 메서드 참조를 그대로 넘길 수 있다) — 이 인터페이스는 그 함수형 자리에 꽂을 "이름 있는"
 * 구현을 만들기 위한 것이지, 엔진 내부 계산 방식을 바꾸는 게 아니다.
 *
 * <p>단위는 항상 "분"이다. {@link HaversineTravelTime} 은 지금 당장 쓸 수 있는 근사치고,
 * {@link KakaoMobilityTravelTime} 이 실제 도로 기반 값으로 대체한다 — 그때도 이 계약은
 * 안 바뀐다.
 */
@FunctionalInterface
public interface TravelTimeProvider {

    double estimate(GeoPoint from, GeoPoint to);
}
