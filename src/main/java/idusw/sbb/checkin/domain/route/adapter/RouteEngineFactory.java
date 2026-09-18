package idusw.sbb.checkin.domain.route.adapter;

import idusw.sbb.checkin.domain.route.engine.DayPlanner;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.route.engine.TravelCostMetric;

import java.util.function.ToDoubleBiFunction;

/**
 * 이동비용 함수를 한 번 만들어 {@link DayPlanner} 와 {@link RouteJsonWriter} 에 <b>같은 것</b>을 넘긴다.
 *
 * <p>둘이 각자 기본값을 만들면, 한쪽 속도만 바뀌었을 때 최적화가 검증한 시각과 화면에 찍히는 시각이
 * 갈라진다. 테스트는 각자 통과하므로 잡히지도 않는다. 그래서 짝을 여기서만 만든다.
 */
public final class RouteEngineFactory {

    private final ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes;

    public RouteEngineFactory(ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes) {
        if (travelTimeMinutes == null) {
            throw new IllegalArgumentException("travelTimeMinutes must not be null");
        }
        this.travelTimeMinutes = travelTimeMinutes;
    }

    /** Haversine · 시속 30km (결정 7-4). */
    public static RouteEngineFactory withDefaults() {
        return new RouteEngineFactory(TravelCostMetric.haversineDefault());
    }

    public DayPlanner dayPlanner() {
        return DayPlanner.withCostMetric(travelTimeMinutes);
    }

    public RouteJsonWriter jsonWriter(CandidateAdapter candidateAdapter) {
        return new RouteJsonWriter(candidateAdapter, travelTimeMinutes);
    }
}
