package idusw.sbb.checkin.domain.system;

import idusw.sbb.checkin.domain.system.controller.PolicyController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개인정보 처리방침이 실제로 내용을 담고 있는지 본다.
 *
 * 구글 OAuth 검수가 「내용이 충분하지 않다」로 한 번 반려했다.
 * 빈 페이지나 제목만 있는 페이지가 다시 나가지 않게 막는다.
 */
class PolicyControllerTest {

    private final String html = new PolicyController().privacy().getBody();

    @Test
    void 항목별_설명이_들어_있다() {
        assertThat(html)
                .contains("수집하는 것")
                .contains("쓰는 곳")
                .contains("위치 정보")
                .contains("보관 기간")
                .contains("이용자가 할 수 있는 것");
    }

    @Test
    void 위치_처리_방식을_사실대로_적는다() {
        /* 「서버로 안 보낸다」고 쓰면 거짓이 된다 — 길 안내는 좌표가 서버를 거친다.
           대신 저장하지 않는다는 것과 기록은 격자라는 것을 적어야 한다. */
        assertThat(html)
                .contains("저장하지 않습니다")
                .contains("격자");
    }

    @Test
    void 챗봇_전송을_숨기지_않는다() {
        assertThat(html).contains("AI 제공사 서버로 전송");
    }

    @Test
    void 사람이_읽을_분량은_된다() {
        assertThat(html.length()).isGreaterThan(2000);
    }
}
