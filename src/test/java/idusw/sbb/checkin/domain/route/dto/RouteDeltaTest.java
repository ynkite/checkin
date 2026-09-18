package idusw.sbb.checkin.domain.route.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 동선 변경의 시간·돈 쌍. 화면에 뜨는 숫자와 같은 규칙에서 나오는지 본다. */
class RouteDeltaTest {

    /** 구간 하나 + 장소 하나짜리 최소 동선. */
    private String route(String transit, String sub) {
        return """
            [{"day":1,"budget":"₩0","places":[
              {"type":"tour","name":"A","sub":"관광지 · 1h"},
              {"transit":"%s"},
              {"type":"food","name":"B","sub":"%s"}
            ]}]""".formatted(transit, sub);
    }

    @Test
    @DisplayName("시간이 줄고 돈이 늘면 부호가 따로 나온다")
    void timeDownMoneyUp() {
        RouteDelta d = RouteDelta.between(
                route("🚌 대중교통 · 8.4km · 약 60분 · ₩1,600", "맛집 · 점심 · ₩10,000×2"),
                route("🚗 자차 · 11.6km · 약 20분 · ₩4,800", "맛집 · 점심 · ₩10,000×2"));

        assertThat(d.minutes()).isEqualTo(-40);
        assertThat(d.won()).isEqualTo(3_200);
    }

    @Test
    @DisplayName("장소 금액의 인원 배수도 센다")
    void countsPerPersonMultiplier() {
        RouteDelta d = RouteDelta.between(
                route("🚶 도보 · 0.4km · 약 6분 · ₩0", "맛집 · 점심 · ₩10,000×2"),
                route("🚶 도보 · 0.4km · 약 6분 · ₩0", "맛집 · 점심 · ₩10,000×4"));

        assertThat(d.won()).isEqualTo(20_000);
        assertThat(d.minutes()).isZero();
    }

    @Test
    @DisplayName("일자 budget 은 구간·장소 금액의 합이라 다시 세지 않는다")
    void dayBudgetIsNotCountedTwice() {
        String before = """
            [{"day":1,"budget":"₩999,999","places":[{"transit":"🚌 · 약 10분 · ₩1,000"}]}]""";
        String after = """
            [{"day":1,"budget":"₩1","places":[{"transit":"🚌 · 약 10분 · ₩1,000"}]}]""";

        assertThat(RouteDelta.between(before, after).won()).isZero();
    }

    @Test
    @DisplayName("이동 문구가 아직 안 채워졌으면 0 이 아니라 모름이다 — 시간도 돈도")
    void unmeasuredTransitIsUnknownNotZero() {
        String placeholder = """
            [{"day":1,"places":[{"transit":"이동"},{"type":"food","name":"B","sub":"맛집 · ₩9,000"}]}]""";
        RouteDelta d = RouteDelta.between(placeholder, route("🚗 자차 · 약 20분 · ₩4,800", "맛집 · ₩9,000"));

        // 못 잰 구간은 시간뿐 아니라 요금도 모른다. 잰 쪽만 빼서 내보내면 그 차액이 사용자 탓이 된다
        assertThat(d.minutes()).isNull();
        assertThat(d.won()).isNull();
    }

    @Test
    @DisplayName("동선을 못 읽으면 쌍이 통째로 비고, 화면은 줄을 내보내지 않는다")
    void brokenRouteYieldsNothing() {
        RouteDelta d = RouteDelta.between("{이건 JSON 이 아니다", null);
        assertThat(d.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("숙소는 하루 앞뒤로 두 번 붙는다 — 맨 앞 행은 어제 묵은 것이라 안 센다")
    void stayRowAtDayStartIsNotCountedAgain() {
        // 1박 2일: Day1 끝에 숙소, Day2 앞에 같은 숙소. 실제로 치르는 건 1박이다
        String before = """
            [{"day":1,"places":[
               {"type":"tour","name":"A","sub":"관광지 · 1h"},
               {"transit":"🚶 도보 · 약 10분 · ₩0"},
               {"type":"stay","name":"호텔","sub":"숙소 · ₩180,000"}]},
             {"day":2,"places":[
               {"type":"stay","name":"호텔","sub":"숙소 · ₩180,000"},
               {"transit":"🚶 도보 · 약 10분 · ₩0"},
               {"type":"tour","name":"B","sub":"관광지 · 1h"}]}]""";
        String after = before.replace("₩180,000", "₩120,000");

        // 180,000 → 120,000 은 1박이므로 −60,000 이다. 두 번 세면 −120,000 이 된다
        assertThat(RouteDelta.between(before, after).won()).isEqualTo(-60_000);
    }

    @Test
    @DisplayName("구간 하나라도 못 재면 합계가 아니다 — 다음에 채워지면 남의 변화를 뒤집어쓴다")
    void partiallyMeasuredRouteIsUnknown() {
        String partial = """
            [{"day":1,"places":[
               {"type":"tour","name":"A","sub":"관광지 · 1h"},
               {"transit":"🚗 자차 · 약 20분 · ₩4,800"},
               {"type":"food","name":"B","sub":"맛집 · ₩9,000"},
               {"transit":"이동"},
               {"type":"cafe","name":"C","sub":"카페 · ₩7,000"}]}]""";
        String full = partial.replace('"' + "이동" + '"', '"' + "🚌 대중교통 · 약 45분 · ₩1,600" + '"');

        RouteDelta d = RouteDelta.between(partial, full);
        assertThat(d.minutes()).isNull();
        assertThat(d.won()).isNull();
        assertThat(d.isEmpty()).isTrue();
    }
}
