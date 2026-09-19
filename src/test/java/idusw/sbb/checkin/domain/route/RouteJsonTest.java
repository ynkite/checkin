package idusw.sbb.checkin.domain.route;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 판정이 무너지면 사용자가 만든 일정이 사라진다.
 *
 * <p>실제로 일어났다. 장소 교체가 모델의 빈 응답을 {@code "[]"} 로 받아 성공 결과로
 * 삼았고, 그게 DB 에 저장되면서 3단계에서 만든 일정이 4단계에서 빈 화면이 됐다.
 */
class RouteJsonTest {

    @Test
    void 들를_곳이_있으면_쓸_만하다() {
        String json = """
                [{"day":1,"places":[
                  {"type":"tour","name":"해운대해수욕장","time":"11:00"}
                ]}]
                """;
        assertThat(RouteJson.usable(json)).isTrue();
    }

    @Test
    void 빈_배열은_쓸_수_없다() {
        /* 이걸 통과시키면 DB 의 동선이 지워진다. */
        assertThat(RouteJson.usable("[]")).isFalse();
    }

    @Test
    void 하루는_있는데_들를_곳이_없으면_쓸_수_없다() {
        assertThat(RouteJson.usable("[{\"day\":1,\"places\":[]}]")).isFalse();
        assertThat(RouteJson.usable("[{\"day\":1}]")).isFalse();
    }

    @Test
    void 이동_칸만_있는_하루는_들를_곳으로_치지_않는다() {
        String json = "[{\"day\":1,\"places\":[{\"transit\":\"지하철 12분\"}]}]";
        assertThat(RouteJson.usable(json)).isFalse();
    }

    @Test
    void 이름이_빈_장소는_들를_곳이_아니다() {
        String json = "[{\"day\":1,\"places\":[{\"type\":\"tour\",\"name\":\"\"}]}]";
        assertThat(RouteJson.usable(json)).isFalse();
    }

    @Test
    void 하루라도_들를_곳이_있으면_쓸_만하다() {
        String json = """
                [{"day":1,"places":[]},
                 {"day":2,"places":[{"type":"food","name":"동래할매파전"}]}]
                """;
        assertThat(RouteJson.usable(json)).isTrue();
    }

    @Test
    void 못_읽는_글자는_쓸_수_없다() {
        assertThat(RouteJson.usable(null)).isFalse();
        assertThat(RouteJson.usable("")).isFalse();
        assertThat(RouteJson.usable("   ")).isFalse();
        assertThat(RouteJson.usable("죄송합니다. 일정을 만들지 못했습니다.")).isFalse();
        assertThat(RouteJson.usable("{\"day\":1}")).isFalse();   // 배열이 아니다
    }
}
