package idusw.sbb.checkin.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.dto.RelatedSpot;
import idusw.sbb.checkin.domain.tour.service.RelatedTourService.Status;
import idusw.sbb.checkin.domain.tour.service.RelatedTourService.Suggestion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripContextPromptTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ObjectMapper om = new ObjectMapper();

    /* trip 1 과 같은 모양 — day · places[name,time,type,lat,lng], 이동 구간은 transit */
    private static final String ROUTE = """
        [
         {"day":1,"places":[{"name":"해운대해수욕장","time":"10:00","type":"tour","lat":35.1587,"lng":129.1604}]},
         {"day":2,"places":[
            {"name":"감천문화마을","time":"09:30","type":"tour","lat":35.0975,"lng":129.0106},
            {"transit":true,"name":"버스"},
            {"name":"자갈치시장","time":"12:30","type":"food","lat":35.0966,"lng":129.0306},
            {"name":"흰여울문화마을","time":"15:00","type":"tour","lat":35.0783,"lng":129.0453}
         ]}
        ]
        """;

    private List<TripContextPrompt.Stop> stops() {
        return TripContextPrompt.stops(om, ROUTE);
    }

    @Test
    void 여행_중이면_현재_시각과_오늘_일정의_좌표와_다음_장소를_넣는다() {
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 18, 13, 5, 0, 0, SEOUL);
        String p = TripContextPrompt.build(now, "부산", LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 19),
                stops(), null, null, null);

        assertThat(p).contains("현재 시각: 2026-09-18(금) 13:05 한국 시각");
        assertThat(p).contains("여행 중, 오늘은 2일차");
        assertThat(p).contains("35.09750,129.01060");
        assertThat(p).contains("15:00 · 흰여울문화마을 · tour · 35.07830,129.04530  <- 다음 장소");
        assertThat(p).doesNotContain("12:30 · 자갈치시장 · food · 35.09660,129.03060  <-");
        assertThat(p).doesNotContain("해운대해수욕장");   // 다른 날 일정은 넣지 않는다
        assertThat(p).doesNotContain("버스");             // 이동 구간은 장소가 아니다
        assertThat(p).contains("기기 위치: 받지 못함");
    }

    @Test
    void 기기_위치가_오면_좌표와_가장_가까운_일정_장소를_넣는다() {
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 18, 13, 0, 0, 0, SEOUL);
        String p = TripContextPrompt.build(now, "부산", LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 19),
                stops(), 35.0970, 129.0300, null);

        assertThat(p).contains("사용자 현재 위치(기기 위치): 위도 35.09700, 경도 129.03000");
        assertThat(p).contains("일정 중 가장 가까운 곳: 자갈치시장");
        assertThat(p).doesNotContain("받지 못함. 위치를");
    }

    @Test
    void 출발_전이면_남은_날과_첫날_일정을_넣고_다음_장소는_표시하지_않는다() {
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 10, 20, 0, 0, 0, SEOUL);
        String p = TripContextPrompt.build(now, "부산", LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 19),
                stops(), null, null, null);

        assertThat(p).contains("출발 전 (출발까지 7일)");
        assertThat(p).contains("1일차 일정");
        assertThat(p).contains("해운대해수욕장");
        assertThat(p).doesNotContain("다음 장소");
    }

    @Test
    void 경로가_없거나_깨졌으면_장소를_지어내지_말라고_적는다() {
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 18, 9, 0, 0, 0, SEOUL);
        List<TripContextPrompt.Stop> broken = TripContextPrompt.stops(om, "{not json");
        String p = TripContextPrompt.build(now, "경주", LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 19),
                broken, null, null, null);

        assertThat(broken).isEmpty();
        assertThat(p).contains("저장된 일정(경로)이 아직 없음");
    }

    @Test
    void 연관_관광지를_받으면_출처와_목록을_넣는다() {
        Suggestion ok = new Suggestion(Status.OK, "경북 경주시", "202608", "불국사", List.of(
                new RelatedSpot("불국사", "국립경주박물관", "관광지", 1, "경상북도", "경주시"),
                new RelatedSpot("불국사", "황리단길", "음식", 4, "경상북도", "경주시")));
        String p = TripContextPrompt.relatedSection(ok);

        assertThat(p).contains("한국관광공사 연관 관광지 정보, 경북 경주시, 2026년 8월 통계");
        assertThat(p).contains("'불국사'에 다녀간 사람들이 함께 많이 간 곳");
        assertThat(p).contains("- 국립경주박물관 (관광지)");
        assertThat(p).contains("- 황리단길 (음식)");
    }

    @Test
    void 날씨는_예보라고_적고_못_받으면_지어내지_말라고_적는다() {
        assertThat(TripContextPrompt.weatherLine(java.util.Map.of("source", "SHORT", "sky", "흐림", "rainProb", 30)))
                .contains("기상청 단기예보, 예보이지 관측값이 아님").contains("흐림").contains("비 올 확률 30%");
        assertThat(TripContextPrompt.weatherLine(java.util.Map.of("source", "SHORT", "rainProb", 70, "outdoorRisk", true)))
                .contains("다음 장소가 야외인데 비 예보가 있음");
        assertThat(TripContextPrompt.weatherLine(java.util.Map.of("source", "NORMAL"))).contains("확인되지 않았습니다");
        assertThat(TripContextPrompt.weatherLine(null)).contains("받지 못함");

        ZonedDateTime now = ZonedDateTime.of(2026, 9, 18, 13, 0, 0, 0, SEOUL);
        String p = TripContextPrompt.build(now, "부산", LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 19),
                stops(), null, null, null, java.util.Map.of("source", "SHORT", "rainProb", 30));
        assertThat(p).contains("비 올 확률 30%").contains("대중교통 노선·환승역·걸리는 시간은 위에 적힌 값이 없으면 말하지 마세요");
    }

    @Test
    void 연관_관광지를_못_받으면_통계를_근거로_대지_말라고_적는다() {
        assertThat(TripContextPrompt.relatedSection(null)).contains("받지 못했습니다");
        assertThat(TripContextPrompt.relatedSection(new Suggestion(Status.NO_AREA, null, null, null, List.of())))
                .contains("지역 코드가 없음");
        assertThat(TripContextPrompt.relatedSection(new Suggestion(Status.UNAVAILABLE, "경북 경주시", null, null, List.of())))
                .contains("관광공사 통계를 근거로 댄 추천을 하지 마세요");
    }
}
