package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.budget.dto.BudgetFitView;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        assertEquals(190_000, f.after(), "10,000 만 넘으니 수목원 하나로 멈춘다 — 케이블카까지 빼면 너무 줄어든다");
        assertTrue(f.changes().get(0).startsWith("유료 관광지·체험 1곳"));

        BudgetFitService.Fit all = BudgetFitService.fit(TRIP, 100_000, 12_000);
        JsonNode days = OM.readTree(all.json());
        assertEquals("수목원", days.get(0).path("places").get(0).path("name").asText(), "1일차 마지막 관광지는 남는다");
        assertEquals("오름", days.get(1).path("places").get(0).path("name").asText(), "2일차 하나뿐인 관광지도 남는다");
    }

    @Test
    void 숙소는_바꾼_뒤_예산_이하이면서_가장_가까운_곳을_고른다() throws Exception {
        List<BudgetFitService.Stay> stays = List.of(
                new BudgetFitService.Stay("아주 싼 곳", 20_000, 1, 33.20, 126.50),        // 100,000 — 너무 줄어든다
                new BudgetFitService.Stay("서귀포 게스트하우스", 50_000, 1, 33.24, 126.55), // 130,000 — 딱 예산
                new BudgetFitService.Stay("조금 싼 호텔", 90_000, 1, 33.22, 126.52),       // 관광지까지 빼도 140,000
                new BudgetFitService.Stay("비싼 호텔", 200_000, 1, 33.2, 126.5));           // 더 비싸면 안 바꾼다
        BudgetFitService.Fit s = BudgetFitService.best(TRIP, 130_000, 12_000, stays);

        assertEquals(200_000, s.before(), "조정 전은 처음 총액 그대로");
        assertEquals(130_000, s.after(), "가장 싼 곳이 아니라 예산에 가장 가까운 곳");
        assertEquals(1, s.changes().size(), "숙소만 바꿔 예산 안이면 관광지는 그대로");
        assertTrue(s.changes().get(0).contains("서귀포 게스트하우스"));
        JsonNode places = OM.readTree(s.json()).get(0).path("places");
        assertEquals("숙소 · ₩50,000", places.get(6).path("sub").asText());
        assertEquals(33.24, places.get(6).path("lat").asDouble());
        assertEquals("이동", places.get(5).path("transit").asText(), "숙소로 가는 길은 다시 잰다");

        assertEquals(170_000, BudgetFitService.best(TRIP, 130_000, 12_000, List.of()).after(), "후보가 없으면 줄일 만큼만");
    }

    @Test
    void 숙소_요금은_객실_수를_곱하고_비현실적으로_싸면_뺀다() {
        assertNull(BudgetFitService.partyStay("캔버스", 21_000, 2, 4, 35.1, 129.1),
                "4명이 2실 42,000원 — 1인 10,500원이라 1인·1실 요금으로 본다");
        BudgetFitService.Stay s = BudgetFitService.partyStay("호텔", 60_000, 0, 4, 35.1, 129.1);
        assertEquals(2, s.rooms(), "기준인원을 모르면 2인실로 본다");
        assertEquals(120_000, s.price());
    }

    @Test
    void 아침_출발_숙소_줄은_값을_달지_않는다() throws Exception {
        var days = (com.fasterxml.jackson.databind.node.ArrayNode) OM.readTree("""
            [{"places":[{"type":"stay","name":"펜션","sub":"숙소 · ₩120,000"}]},
             {"places":[{"type":"stay","name":"펜션","sub":"숙소 · 출발"}]}]""");
        String change = BudgetFitService.swapStay(days, new BudgetFitService.Stay("호텔", 100_000, 2, 35.1, 129.1));

        assertTrue(change.contains("2실"));
        assertEquals("숙소 · 2실 · ₩100,000", days.get(0).path("places").get(0).path("sub").asText());
        assertEquals("숙소 · 출발", days.get(1).path("places").get(0).path("sub").asText());
        assertEquals("호텔", days.get(1).path("places").get(0).path("name").asText());
    }

    @Test
    void 공개_요금이_없어_평균으로_매긴_숙소는_추정이라_적는다() throws Exception {
        BudgetFitService.Stay s = BudgetFitService.partyStay("해운대 게스트하우스", 35_000, 0, 4, 35.16, 129.16, true);
        assertEquals(70_000, s.price(), "평균 1실 35,000 × 2실");
        assertTrue(s.estimated());
        assertNull(BudgetFitService.partyStay("싼 곳", 25_000, 0, 4, 35.16, 129.16, true), "1인 12,500원 — 뺀다");

        var days = (com.fasterxml.jackson.databind.node.ArrayNode) OM.readTree("""
            [{"places":[{"type":"stay","name":"바다풍경펜션","sub":"숙소 · ₩120,000"}]}]""");
        String change = BudgetFitService.swapStay(days, s);
        assertTrue(change.contains("1박 약 70,000원 · 추정"));
        assertEquals("숙소 · 2실 · 추정 · ₩70,000", days.get(0).path("places").get(0).path("sub").asText());
    }

    @Test
    void 가까움은_예산_안이_먼저다() {
        assertTrue(BudgetFitService.closer(590_000, 550_000, 600_000));
        assertTrue(BudgetFitService.closer(590_000, 610_000, 600_000));
        assertTrue(BudgetFitService.closer(620_000, 650_000, 600_000));
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

        BudgetFitService svc = new BudgetFitService(repo, route, new BudgetLimitService(), null, tx, null, null);

        assertDoesNotThrow(() -> svc.applyTo(1L));
        verify(route, never()).saveAiRouteToDb(any(), any());

        BudgetFitView v = svc.view(1L);
        assertTrue(v.fallback(), "화면은 가계부 총액으로 % 만 낸다");
        assertTrue(v.changes().isEmpty());
        assertThrows(Exception.class, () -> BudgetFitService.fit("깨진 JSON", 100_000, 12_000));
    }

    // 숙소 100,000 × 1박, 서귀포
    private static final String STAY_ROUTE = """
        [{"day":1,"places":[
           {"type":"tour","name":"쇠소깍","sub":"관광지 · ₩0"},
           {"type":"stay","name":"바다풍경펜션","sub":"숙소 · ₩100,000","lat":33.25,"lng":126.62}]}]
        """;

    @Test
    void 예산이_한참_남으면_여유와_올려도_되는_근처_숙소를_권한다() throws Exception {
        JsonNode days = OM.readTree(STAY_ROUTE);
        String line = BudgetFitService.spareLine(days, 300_000, List.of(
                new BudgetFitService.Stay("싼 민박", 60_000, 1, 33.2, 126.6),
                new BudgetFitService.Stay("오션호텔", 250_000, 1, 33.2, 126.6, true),
                new BudgetFitService.Stay("너무 비싼 리조트", 500_000, 1, 33.2, 126.6)));
        assertEquals("예산보다 300,000원 여유가 있어요. 근처 오션호텔(1박 약 250,000원)로 숙소를 올려도 예산 안이에요.", line);
        assertTrue(BudgetFitService.spareLine(days, 10_000, List.of()).endsWith("하나 더 넣어도 예산 안이에요."));
        assertNull(BudgetFitService.spareLine(days, 0, List.of()));
    }

    @Test
    void 숙소_바꾸기_진단은_후보_수와_떨어진_이유를_센다() {
        var kor = mock(idusw.sbb.checkin.domain.tour.service.KorTourService.class);
        var rate = mock(idusw.sbb.checkin.domain.tour.service.LodgingRateService.class);
        java.util.function.BiFunction<String, String, idusw.sbb.checkin.domain.tour.service.KorTourService.Lodging> l =
                (id, name) -> new idusw.sbb.checkin.domain.tour.service.KorTourService.Lodging(
                        new idusw.sbb.checkin.domain.tour.dto.Spot(id, name, 33.25, 126.62, "39", "4", 32, null, null), "B02010700");
        when(kor.nearbyLodging(anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(List.of(
                l.apply("1", "바다풍경펜션"), l.apply("2", "비싼 호텔"), l.apply("3", "싼 민박"), l.apply("4", "요금 없는 곳")));
        when(rate.publicRoom(eq("2"), any())).thenReturn(new long[]{200_000, 4});
        when(rate.publicRoom(eq("3"), any())).thenReturn(new long[]{70_000, 4});
        when(rate.publicRoom(eq("4"), any())).thenReturn(new long[]{0, 0});
        when(rate.regional(any(), any(), any())).thenReturn(idusw.sbb.checkin.domain.tour.dto.LodgingRate.none("비수기 주중"));
        when(rate.regional(any(), any())).thenReturn(idusw.sbb.checkin.domain.tour.dto.LodgingRate.none("비수기 주중"));

        BudgetFitService svc = new BudgetFitService(null, null, null, rate, null, kor, null);
        var p = svc.probe(STAY_ROUTE, java.time.LocalDate.of(2026, 10, 30), 4, 0);

        assertEquals(4, p.tried().found());
        assertEquals(2, p.tried().priced());
        assertEquals(1, p.tried().dropped().get("지금 숙소"));
        assertEquals(1, p.tried().dropped().get("요금 못 매김"));
        assertEquals(1, p.tried().dropped().get("지금 숙소(100,000원)보다 비쌈"));
        assertNull(p.tried().note());
    }

    @Test
    void 숙소_좌표가_없으면_진단에_그렇게_적는다() {
        BudgetFitService svc = new BudgetFitService(null, null, null, null, null, null, null);
        var p = svc.probe(ROUTE, java.time.LocalDate.of(2026, 10, 30), 2, 0);
        assertEquals("지금 숙소 좌표가 없음", p.tried().note());
        assertTrue(p.stays().isEmpty());
    }

    @Test
    void 숙소_비교는_지금_숙소의_인원_추가까지_더한_일행_전체_1박으로_한다() throws Exception {
        // 지금 숙소 100,000 + 인원 추가 30,000(1박) = 130,000. 120,000 짜리는 일정 금액보다 비싸도 더 싸다
        var days = (com.fasterxml.jackson.databind.node.ArrayNode) OM.readTree(STAY_ROUTE);
        var to = new BudgetFitService.Stay("근처 펜션", 120_000, 2, 33.25, 126.62);
        assertNull(BudgetFitService.swapStay((com.fasterxml.jackson.databind.node.ArrayNode) OM.readTree(STAY_ROUTE), to));
        String change = BudgetFitService.swapStay(days, to, 30_000);
        assertTrue(change.endsWith("−10,000원 (인원 추가 포함)"), change);

        // 일정 몫 예산 95,000. 바꾸면 인원 추가 30,000 이 사라지므로 120,000 − 30,000 = 90,000 으로 견줘 예산 안
        BudgetFitService.Fit f = BudgetFitService.best(STAY_ROUTE, 95_000, 12_000, List.of(to), 30_000);
        assertTrue(f.changes().get(0).startsWith("숙소를 근처 근처 펜션"));
    }
}
