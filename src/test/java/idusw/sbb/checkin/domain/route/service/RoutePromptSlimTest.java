package idusw.sbb.checkin.domain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.route.tmap.TravelTimeService;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelLeg;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 실내 전환·장소 교체 프롬프트에는 저장 때 다시 재는 칸을 넣지 않는다. 빠진 칸은 저장 보정이 다시 채운다. */
class RoutePromptSlimTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void 프롬프트용_JSON_에서_다시_재는_칸만_뺀다() throws Exception {
        String stored = """
            [{"day":1,"label":"1일차","budget":"₩10,000","legTotalMinutes":30,"wxLabel":"비","budgetFit":{"changes":["카페 뺌"]},
              "places":[{"type":"tour","name":"동백섬","sub":"관광지 · 1h · ₩0×2","lat":35.15,"lng":129.15,"addr":"부산 해운대구",
                         "crowd":88,"crowdLabel":"혼잡","wx":60,"wxSource":"SHORT","outdoorRisk":true,"legMinutes":9,"legDistance":3200},
                        {"transit":"🚗 자차 · 3.2km · 약 9분 · ₩640","pathCoords":[[35.1,129.1],[35.2,129.2]]}]}]
            """;
        JsonNode day = OM.readTree(AiRouteService.forPrompt(stored)).get(0);
        JsonNode place = day.path("places").get(0);

        for (String k : List.of("legTotalMinutes", "wxLabel")) assertFalse(day.has(k), k);
        for (String k : List.of("crowd", "crowdLabel", "wx", "wxSource", "legMinutes", "legDistance"))
            assertFalse(place.has(k), k);
        assertFalse(day.path("places").get(1).has("pathCoords"));

        // 모델이 판단에 쓰는 것과 저장 때 다시 만들지 않는 것은 남는다
        assertEquals("동백섬", place.path("name").asText());
        assertEquals(35.15, place.path("lat").asDouble());
        assertTrue(place.path("outdoorRisk").asBoolean());
        assertEquals("카페 뺌", day.path("budgetFit").path("changes").get(0).asText());
        assertEquals("🚗 자차 · 3.2km · 약 9분 · ₩640", day.path("places").get(1).path("transit").asText());

        assertEquals("깨진 JSON", AiRouteService.forPrompt("깨진 JSON"));
    }

    @Test
    void 빠진_칸은_저장_보정이_다시_채운다() throws Exception {
        RestTemplate rest = mock(RestTemplate.class);
        // 카카오 키워드(좌표) — 이름마다 다른 좌표
        when(rest.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> {
                    boolean dongbaek = inv.getArgument(0, URI.class).toString().contains("%EB%8F%99%EB%B0%B1");
                    String xy = dongbaek ? "\"x\":\"129.15\",\"y\":\"35.15\"" : "\"x\":\"129.16\",\"y\":\"35.16\"";
                    return ResponseEntity.ok("{\"documents\":[{\"address_name\":\"부산 해운대구 우동\"," + xy + "}]}");
                });
        // 카카오 길찾기
        when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                    {"routes":[{"summary":{"distance":3200,"duration":540,"fare":{"toll":0}},
                      "sections":[{"roads":[{"vertexes":[129.15,35.15,129.155,35.155,129.16,35.16]}]}]}]}
                    """));
        TravelTimeService tmap = mock(TravelTimeService.class);
        when(tmap.plan(any())).thenReturn(new idusw.sbb.checkin.domain.route.tmap.dto.TravelPlan(
                true, "CAR", "동백섬", 35.15, 129.15,
                List.of(new TravelLeg("동백섬", "해운대해수욕장", "CAR", 9, 3200, 0, null, "")),
                9, 3200, 0, ""));

        AiRouteService svc = new AiRouteService(
                mock(idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository.class),
                mock(idusw.sbb.checkin.domain.expense.repository.ExpenseRepository.class),
                mock(idusw.sbb.checkin.domain.place.service.PlaceService.class),
                mock(idusw.sbb.checkin.domain.crowd.CrowdService.class),
                mock(idusw.sbb.checkin.domain.weather.service.WeatherService.class),
                tmap,
                mock(idusw.sbb.checkin.domain.place.repository.PlaceRepository.class),
                mock(idusw.sbb.checkin.domain.tour.service.TourAreaInfoService.class),
                mock(idusw.sbb.checkin.domain.route.tmap.NaviService.class),
                OM, rest, mock(ChatModel.class), mock(ChatModel.class), mock(ChatModel.class));

        PlanInputForm form = mock(PlanInputForm.class);
        when(form.getCompanionCount()).thenReturn(2);
        when(form.getTransportType()).thenReturn("자차");
        TravelPlan plan = mock(TravelPlan.class);
        when(plan.getForm()).thenReturn(form);
        when(plan.getDestination()).thenReturn("부산 해운대구");
        when(plan.getStartDate()).thenReturn(LocalDate.of(2026, 10, 23));
        when(plan.getEndDate()).thenReturn(LocalDate.of(2026, 10, 24));

        // 모델이 돌려준 모양 — 좌표·도로선·구간 측정값이 하나도 없다
        String fromModel = """
            [{"day":1,"label":"1일차","budget":"₩0","places":[
              {"type":"tour","name":"동백섬","sub":"관광지 · 1h · ₩0×2","time":"10:00"},
              {"transit":"이동"},
              {"type":"tour","name":"해운대해수욕장","sub":"관광지 · 1h · ₩0×2","time":"12:00"}]}]
            """;
        JsonNode places = OM.readTree(svc.enrich(plan, fromModel)).get(0).path("places");

        assertEquals(35.15, places.get(0).path("lat").asDouble());
        assertEquals(3, places.get(1).path("pathCoords").size(), "도로선");
        assertTrue(places.get(1).path("transit").asText().contains("3.2km"), "구간 거리·시간");
        assertEquals(9, places.get(2).path("legMinutes").asInt(), "TMAP 이동시간");
        assertEquals(3200, places.get(2).path("legDistance").asInt());
    }
}
