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
        assertEquals(124_000, f.after());   // 숙소는 바꾸지 않는다
    }

    @Test
    void 깨진_일정은_예외를_던지고_서비스는_원래_일정을_둔다() {
        TravelPlanRepository repo = mock(TravelPlanRepository.class);
        when(repo.findById(any())).thenReturn(Optional.empty());
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null));
        var route = mock(idusw.sbb.checkin.domain.route.service.AiRouteService.class);

        BudgetFitService svc = new BudgetFitService(repo, route, new BudgetLimitService(), null, tx);

        assertDoesNotThrow(() -> svc.applyTo(1L));
        verify(route, never()).saveAiRouteToDb(any(), any());

        BudgetFitView v = svc.view(1L);
        assertTrue(v.fallback(), "화면은 가계부 총액으로 % 만 낸다");
        assertTrue(v.changes().isEmpty());
        assertThrows(Exception.class, () -> BudgetFitService.fit("깨진 JSON", 100_000, 12_000));
    }
}
