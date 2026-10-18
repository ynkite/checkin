package idusw.sbb.checkin.domain.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyTextTest {

    @Test
    void 맨_앞_인사와_그림_문자를_뗀다() {
        assertThat(ReplyText.tidy("안녕하세요! 체크인입니다. 해운대구 맛집은 해목입니다. 😀"))
                .isEqualTo("해운대구 맛집은 해목입니다.");
        assertThat(ReplyText.tidy("안녕하세요 밥은 해목에서 드세요.")).isEqualTo("밥은 해목에서 드세요.");
        assertThat(ReplyText.tidy("낮 기온 20℃, 비 올 확률 30%입니다.")).isEqualTo("낮 기온 20℃, 비 올 확률 30%입니다.");
        assertThat(ReplyText.tidy(null)).isEmpty();
    }

    @Test
    void 음성용은_태그를_떼고_화면용_본문은_그대로_둔다() {
        String r = "예산을 줄이겠습니다. [UPDATE:BUDGET:₩300,000][EXTRA:식당조건:반려동물 동반 가능]";
        assertThat(ReplyText.untag(r)).isEqualTo("예산을 줄이겠습니다.");
        assertThat(ReplyText.tidy(r)).isEqualTo(r);
    }
}
