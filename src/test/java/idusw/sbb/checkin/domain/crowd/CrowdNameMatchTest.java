package idusw.sbb.checkin.domain.crowd;

import org.junit.jupiter.api.Test;

import static idusw.sbb.checkin.domain.crowd.CrowdService.matches;
import static idusw.sbb.checkin.domain.crowd.CrowdService.norm;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 집중률 장소 이름을 맞추는 자리다.
 *
 * <p><b>왜 생겼나</b> — 메인 화면의 카페 「시드니앤솔트 마린시티점」에
 * <b>집중률 62.51 이 붙어 있었다.</b> 집중률 목록에 「마린시티」가 있는데,
 * 「품고 있으면 같은 곳」이라는 규칙 때문에 걸린 것이다.
 *
 * <p>집중률 대상이 아닌 곳에 진짜처럼 보이는 숫자가 붙는 것은
 * 값이 없는 것보다 나쁘다. 「확인되지 않음」이라고 말해야 할 자리다.
 */
class CrowdNameMatchTest {

    private boolean same(String a, String b) { return matches(norm(a), norm(b)); }

    @Test
    void 카페_이름에_들어간_동네_이름으로_붙지_않는다() {
        /* 배포에서 실제로 나온 값이다. 62.51 이 카페에 붙어 있었다 */
        assertThat(same("마린시티", "시드니앤솔트 마린시티점")).isFalse();
    }

    @Test
    void 공식_이름이_더_길어도_같은_곳은_맞춘다() {
        assertThat(same("스파랜드 센텀", "스파랜드 센텀시티")).isTrue();
        assertThat(same("부산아쿠아리움", "SEA LIFE 부산아쿠아리움")).isTrue();
    }

    @Test
    void 공백과_기호만_다르면_같은_곳이다() {
        assertThat(same("해운대해수욕장", "해운대 해수욕장")).isTrue();
        assertThat(same("감천문화마을", "감천 문화마을")).isTrue();
    }

    @Test
    void 아예_다른_곳은_안_맞춘다() {
        assertThat(same("동백섬", "광안리해수욕장")).isFalse();
        assertThat(same("해운대해수욕장", "광안리해수욕장")).isFalse();
    }

    @Test
    void 빈_이름은_아무것도_안_맞춘다() {
        assertThat(same("", "미포")).isFalse();
        assertThat(same("미포", "")).isFalse();
    }
}
