package idusw.sbb.checkin.domain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 2박인데 숙소 값이 4번 셈해지던 것 — 아침 출발 줄은 값을 달지 않는다. */
class RouteDepartureStayTest {

    @Test
    void 이틀째부터_아침_첫_숙소는_값을_지운다() throws Exception {
        String json = """
            [{"day":1,"places":[{"type":"tour","name":"동백섬","sub":"관광지"},{"transit":"이동"},
                                {"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"}]},
             {"day":2,"places":[{"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"},{"transit":"이동"},
                                {"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"}]},
             {"day":3,"places":[{"type":"stay","name":"캔버스","sub":"숙소 · ₩120,000"}]}]
            """;
        JsonNode days = new ObjectMapper().readTree(idusw.sbb.checkin.domain.route.RouteJson.unpriceDepartureStay(json));

        assertEquals("숙소 · ₩120,000", days.get(0).path("places").get(2).path("sub").asText(), "1일차 밤은 그대로");
        assertEquals("숙소 · 출발", days.get(1).path("places").get(0).path("sub").asText());
        assertEquals("숙소 · ₩120,000", days.get(1).path("places").get(2).path("sub").asText(), "2일차 밤은 그대로");
        assertEquals("숙소 · 출발", days.get(2).path("places").get(0).path("sub").asText());
        assertEquals("깨진 JSON", idusw.sbb.checkin.domain.route.RouteJson.unpriceDepartureStay("깨진 JSON"));
    }
}
