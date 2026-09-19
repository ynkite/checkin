package idusw.sbb.checkin.domain.crowd.check;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.crowd.check.PlaceCheck.Finding;
import idusw.sbb.checkin.domain.crowd.check.PlaceCheck.Kind;
import idusw.sbb.checkin.domain.crowd.check.PlaceCheck.Option;
import idusw.sbb.checkin.domain.crowd.check.PlaceCheck.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceCheckTest {

    private static final Map<String, Object> PLACE = Map.of("name", "감천문화마을", "time", "10:40");

    private static final Map<String, Map<String, Object>> ACTIONS = Map.of(
            "indoor", Map.of("key", "indoor", "label", "실내로 바꾸기", "note", "그날 야외 일정을 실내로 다시 짭니다",
                    "method", "POST", "endpoint", "/api/trips/1/routes/indoor-replace?day=1", "oneClick", true),
            "quiet", Map.of("quietEndpoint", "/api/live/quiet?tripId=1&day=1&name=x"));

    private Finding ok(Kind k, boolean issue, String src) {
        return new Finding(k, Status.OK, issue, src, src, "", Map.of());
    }

    private Finding miss(Kind k) {
        return new Finding(k, Status.UNAVAILABLE, false, "NONE", "", "", Map.of());
    }

    private List<String> keys(PlaceCheck.Result r) {
        return r.options().stream().map(Option::key).toList();
    }

    @Test
    void 문제가_없으면_그대로_가도_된다고_말한다() {
        PlaceCheck.Result r = PlaceCheck.decide("LIVE", "지금 상황", "2026-09-19", "t", PLACE,
                List.of(ok(Kind.CROWD, false, "TOUR_FORECAST"), ok(Kind.RAIN, false, "KMA_SHORT"), miss(Kind.TRAFFIC)),
                ACTIONS, null);
        assertThat(r.verdict().status()).isEqualTo("OK");
        assertThat(r.verdict().text()).contains("그대로 가도 됩니다");
        assertThat(keys(r)).containsExactly("keep");
    }

    @Test
    void 아무것도_확인하지_못했으면_괜찮다고_하지_않는다() {
        PlaceCheck.Result r = PlaceCheck.decide("LIVE", "지금 상황", "2026-09-19", "t", PLACE,
                List.of(miss(Kind.CROWD), miss(Kind.RAIN), miss(Kind.TRAFFIC)), ACTIONS, null);
        assertThat(r.verdict().status()).isEqualTo("UNKNOWN");
        assertThat(r.verdict().text()).contains("확인하지 못했습니다").doesNotContain("그대로 가도");
    }

    @Test
    void 비가_오면_한_번에_끝나는_실내_갈래와_그대로_가기를_둘_다_준다() {
        PlaceCheck.Result r = PlaceCheck.decide("LIVE", "지금 상황", "2026-09-19", "t", PLACE,
                List.of(ok(Kind.RAIN, true, "KMA_SHORT")), ACTIONS, null);
        assertThat(r.verdict().status()).isEqualTo("ISSUE");
        assertThat(keys(r)).containsExactly("indoor", "keep");
        Option indoor = r.options().get(0);
        assertThat(indoor.oneClick()).isTrue();
        assertThat(indoor.endpoint()).isEqualTo("/api/trips/1/routes/indoor-replace?day=1");
    }

    @Test
    void 붐비면_순서_바꾸기와_한적한_곳을_주고_당일과_예측의_말이_다르다() {
        PlaceCheck.Result today = PlaceCheck.decide("LIVE", "지금 상황", "d", "t", PLACE,
                List.of(ok(Kind.CROWD, true, "SK_LIVE")), ACTIONS, null);
        PlaceCheck.Result later = PlaceCheck.decide("FORECAST", "9월 25일 예측", "d", "t", PLACE,
                List.of(ok(Kind.CROWD, true, "TOUR_FORECAST")), ACTIONS, null);
        assertThat(keys(today)).containsExactly("swap", "quiet", "keep");
        assertThat(today.verdict().text()).contains("지금 붐빕니다");
        assertThat(later.verdict().text()).contains("그날 붐빌 것 같습니다").doesNotContain("지금");
        assertThat(today.options().get(1).endpoint()).startsWith("/api/live/quiet");
    }

    @Test
    void 당일이라도_실측을_못_받았으면_지금_붐빈다고_하지_않는다() {
        PlaceCheck.Result r = PlaceCheck.decide("LIVE", "지금 상황", "d", "t", PLACE,
                List.of(ok(Kind.CROWD, true, "TOUR_FORECAST")), ACTIONS, null);
        assertThat(r.verdict().text()).contains("오늘 붐빌 것으로 예측됩니다").doesNotContain("지금 붐빕니다");
    }

    @Test
    void 늦으면_몇_분인지와_출발_갈래를_준다() {
        PlaceCheck.Result r = PlaceCheck.decide("LIVE", "지금 상황", "d", "t", PLACE,
                List.of(ok(Kind.TRAFFIC, true, "TMAP_LIVE")), ACTIONS, 22);
        assertThat(r.verdict().text()).contains("22분 늦습니다");
        assertThat(keys(r)).containsExactly("leave_now", "other_mode", "navi", "keep");
    }

    @Test
    void 평년값_날은_비를_판단하지_않고_예보_아님이라고_적는다() {
        Finding f = PlaceCheckService.weather(Map.of("source", "NORMAL", "tempMin", 12));
        assertThat(f.status()).isEqualTo(Status.NO_DATA);
        assertThat(f.issue()).isFalse();
        assertThat(f.sourceLabel()).isEqualTo("평년값(예보 아님)");

        Finding rain = PlaceCheckService.weather(Map.of("source", "SHORT", "rainProb", 80, "outdoorRisk", true));
        assertThat(rain.issue()).isTrue();
        assertThat(rain.source()).isEqualTo("KMA_SHORT");
        assertThat(rain.summary()).contains("80%");

        assertThat(PlaceCheckService.weather(Map.of()).status()).isEqualTo(Status.UNAVAILABLE);
    }

    @Test
    void TMAP_응답에서_걸리는_시간을_읽는다() throws Exception {
        var om = new ObjectMapper();
        assertThat(PlaceCheckService.totalSeconds(om.readTree(
                "{\"features\":[{\"properties\":{\"totalDistance\":14043,\"totalTime\":2760}}]}"))).isEqualTo(2760);
        assertThat(PlaceCheckService.totalSeconds(om.readTree("{\"features\":[]}"))).isNull();
        assertThat(PlaceCheckService.totalSeconds(null)).isNull();
    }
}
