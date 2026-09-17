package idusw.sbb.checkin.domain.route.engine;

/**
 * 앵커 기준 밴드 라벨. 경계값·분류 로직은 여기 없다 — {@link BandSplitter} 가 갖는다.
 * {@code RETURN} 은 거리가 아니라 우회비용(《설계 결정 9/12》 결정 2) 기준이라
 * 과거의 A/B/C 라는 거리스러운 이름을 쓰지 않는다 (결정 5-(1)).
 */
public enum DistanceBand {
    NEAR,
    MID,
    RETURN
}
