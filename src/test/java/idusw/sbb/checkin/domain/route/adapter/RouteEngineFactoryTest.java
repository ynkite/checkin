package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.route.engine.Candidate;
import idusw.sbb.checkin.domain.route.engine.DayPlan;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.route.engine.RouteConstraints;
import idusw.sbb.checkin.domain.route.engine.SlotType;
import idusw.sbb.checkin.domain.route.engine.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteEngineFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GeoPoint ANCHOR_POINT = new GeoPoint(37.8038, 128.9087);

    private static ObjectNode node(String name, double km) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", name);
        node.put("lat", ANCHOR_POINT.latitude() + km / 111.194);
        node.put("lng", ANCHOR_POINT.longitude());
        return node;
    }

    private static RouteConstraints constraints() {
        return new RouteConstraints(null, null,
                LocalTime.of(12, 0), LocalTime.of(13, 30),
                LocalTime.of(18, 0), LocalTime.of(19, 30),
                null, ANCHOR_POINT, null, null, null);
    }

    @Test
    void 비용_함수를_바꾸면_계획과_출력_시각이_함께_따라온다() {
        // 거리를 무시하고 구간마다 무조건 10분. 기본값(Haversine/30km)과 확연히 다른 값이라,
        // writer 가 팩토리의 함수 대신 자기 기본값을 쓰면 시각이 어긋나 이 테스트가 깨진다.
        ToDoubleBiFunction<GeoPoint, GeoPoint> tenMinutes = (from, to) -> 10.0;
        RouteEngineFactory factory = new RouteEngineFactory(tenMinutes);

        CandidateAdapter adapter = new CandidateAdapter();
        Map<String, List<ObjectNode>> nodes = new LinkedHashMap<>();
        nodes.put("tour", List.of(node("경포해수욕장", 1), node("경포호", 3)));
        List<Candidate> tours = adapter.toCandidates(nodes);

        DayPlan dayPlan = factory.dayPlanner().plan(
                List.of(new TimeSlot(SlotType.MORNING_ACTIVITY, tours)),
                ANCHOR_POINT, ANCHOR_POINT, constraints(), 0, 1);
        ArrayNode root = factory.jsonWriter(adapter)
                .write(List.of(dayPlan), null, constraints(), null);

        List<String> times = new ArrayList<>();
        for (JsonNode place : root.get(0).path("places")) {
            if (!place.has("transit")) {
                times.add(place.path("time").asText());
            }
        }
        // 09:00 + 10분 = 09:10 도착, 체류 90분 → 10:40 출발, + 10분 = 10:50 도착
        assertThat(times).containsExactly("09:10", "10:50");
        assertThat(dayPlan.slotPlans().get(0).endTime()).isEqualTo(LocalTime.of(12, 20));
    }

    @Test
    void 비용_함수가_없으면_예외() {
        assertThatThrownBy(() -> new RouteEngineFactory(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
