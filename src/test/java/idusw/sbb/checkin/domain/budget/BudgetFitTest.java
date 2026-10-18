package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.budget.dto.BudgetFitView;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 예산 맞춤 — 넘는 만큼만 줄이고, 못 줄이거나 실패하면 원래 일정을 그대로 둔다. */
class BudgetFitTest {

    private static final ObjectMapper OM = new ObjectMapper();

    // 숙소 100,000 + 점심 20,000×2 + 카페 8,000×2 + 이동 3,000 = 159,000
    private static final String ROUTE = """
        [{"day":1,"budget":"₩159,000","places":[
           {"type":"food","name":"밀면집","sub":"맛집 · 점심 · ₩20,000×2"},
           {"transit":"차 · 2.0km · 약 5분 · ₩3,000"},
           {"type":"cafe","name":"바다카페","sub":"카페 · ₩8,000×2"},
           {"transit":"차 · 1.0km · 약 3분 · ₩0"},
           {"type":"stay","name":"해운대 호텔","sub":"숙소 · ₩100,000"}]}]
        """;

    @Test
    void 총액은_장소와_이동_금액의_합이다() throws Exception {
        assertEquals(159_000, BudgetFitService.total(OM.readTree(ROUTE)));
    }

    @Test
    void 예산_안이면_손대지_않는다() throws Exception {
        BudgetFitService.Fit f = BudgetFitService.fit(ROUTE, 200_000, 12_000);
        assertFalse(f.changed());
        assertSame(ROUTE, f.json());
        assertEquals(80, BudgetFitService.percent(f.after(), 200_000));
    }

    @Test
    void 끼니부터_낮추고_넘는_만큼만_줄인다() throws Exception {
        // 끼니 20,000 → 12,000 (×2) 로 16,000 줄면 143,000. 145,000 안이라 카페는 남는다
        BudgetFitService.Fit f = BudgetFitService.fit(ROUTE, 145_000, 12_000);

        assertTrue(f.changed());
        assertEquals(159_000, f.before());
        assertEquals(143_000, f.after());
        assertEquals(1, f.changes().size());
        assertTrue(f.changes().get(0).contains("평균 기준"));

        JsonNode day = OM.readTree(f.json()).get(0);
        assertEquals("맛집 · 점심 · ₩12,000×2", day.path("places").get(0).path("sub").asText());
        assertEquals(159_000, day.path("budgetFit").path("before").asLong(), "화면이 「전 → 후」를 그릴 값");
    }

    @Test
    void 그래도_넘으면_카페를_빼고_이동_구간을_다시_재게_한다() throws Exception {
        BudgetFitService.Fit f = BudgetFitService.fit(ROUTE, 130_000, 12_000);

        JsonNode places = OM.readTree(f.json()).get(0).path("places");
        assertEquals(3, places.size(), "카페와 붙은 이동 한 구간이 같이 빠진다");
        assertEquals("이동", places.get(1).path("transit").asText(), "남은 구간은 저장할 때 카카오가 다시 잰다");
        assertEquals(2, f.changes().size());
        assertEquals(124_000, f.after());   // 숙소는 fit 에서 바꾸지 않는다
    }

    // 관광지 2곳(15,000×2 · 5,000×2) + 숙소 120,000 + 점심 12,000×2 + 2일차 8,000×2 = 200,000
    private static final String TRIP = """
        [{"day":1,"places":[
           {"type":"tour","name":"케이블카","sub":"관광지 · 1h · ₩15,000×2"},
           {"transit":"차 · ₩0"},
           {"type":"tour","name":"수목원","sub":"관광지 · ₩5,000×2"},
           {"transit":"차 · ₩0"},
           {"type":"food","name":"국수집","sub":"맛집 · 점심 · ₩12,000×2"},
           {"transit":"차 · ₩0"},
           {"type":"stay","name":"바다풍경펜션","sub":"숙소 · ₩120,000","lat":33.25,"lng":126.56}]},
         {"day":2,"places":[
           {"type":"tour","name":"오름","sub":"관광지 · ₩8,000×2"}]}]
        """;

    @Test
    void 유료_관광지는_비싼_것부터_빼되_그날_하나뿐이면_남긴다() throws Exception {
        BudgetFitService.Fit f = BudgetFitService.fit(TRIP, 190_000, 12_000);

        assertEquals(200_000, f.before());
        assertEquals(170_000, f.after(), "케이블카 30,000 만 빠지면 예산 안");
        assertTrue(f.changes().get(0).startsWith("유료 관광지·체험 1곳"));

        BudgetFitService.Fit all = BudgetFitService.fit(TRIP, 100_000, 12_000);
        JsonNode days = OM.readTree(all.json());
        assertEquals("수목원", days.get(0).path("places").get(0).path("name").asText(), "1일차 마지막 관광지는 남는다");
        assertEquals("오름", days.get(1).path("places").get(0).path("name").asText(), "2일차 하나뿐인 관광지도 남는다");
    }

    @Test
    void 숙소는_더_싼_근처_숙소로_바꾸고_좌표도_옮긴다() throws Exception {
        BudgetFitService.Fit f = BudgetFitService.fit(TRIP, 150_000, 12_000);
        BudgetFitService.Fit s = BudgetFitService.swapStay(f, new BudgetFitService.Stay("서귀포 게스트하우스", 50_000, 33.24, 126.55));

        assertEquals(200_000, s.before(), "조정 전은 처음 총액 그대로");
        assertEquals(f.after() - 70_000, s.after());
        assertTrue(s.changes().get(s.changes().size() - 1).contains("서귀포 게스트하우스"));
        JsonNode stay = OM.readTree(s.json()).get(0).path("places").get(4);
        assertEquals("숙소 · ₩50,000", stay.path("sub").asText());
        assertEquals(33.24, stay.path("lat").asDouble());
        assertEquals("이동", OM.readTree(s.json()).get(0).path("places").get(3).path("transit").asText(), "숙소로 가는 길은 다시 잰다");

        assertSame(f, BudgetFitService.swapStay(f, new BudgetFitService.Stay("비싼 호텔", 200_000, 33.2, 126.5)),
                "더 비싸면 바꾸지 않는다");
    }

    @Test
    void 그래도_넘으면_얼마나_넘는지와_줄일_곳을_한_줄로_알린다() {
        assertNull(BudgetFitService.overLine(590_000, 600_000, "펜션", 120_000));
        String line = BudgetFitService.overLine(723_016, 600_000, "바다풍경펜션", 120_000);
        assertTrue(line.startsWith("예산보다 123,016원 많아요."));
        assertTrue(line.contains("바다풍경펜션(120,000원)"));
    }

    @Test
    void 깨진_일정은_예외를_던지고_서비스는_원래_일정을_둔다() {
        TravelPlanRepository repo = mock(TravelPlanRepository.class);
        when(repo.findById(any())).thenReturn(Optional.empty());
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null));
        var route = mock(idusw.sbb.checkin.domain.route.service.AiRouteService.class);

        BudgetFitService svc = new BudgetFitService(repo, route, new BudgetLimitService(), null, tx, null);

        assertDoesNotThrow(() -> svc.applyTo(1L));
        verify(route, never()).saveAiRouteToDb(any(), any());

        BudgetFitView v = svc.view(1L);
        assertTrue(v.fallback(), "화면은 가계부 총액으로 % 만 낸다");
        assertTrue(v.changes().isEmpty());
        assertThrows(Exception.class, () -> BudgetFitService.fit("깨진 JSON", 100_000, 12_000));
    }
}
