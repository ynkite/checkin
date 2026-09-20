package idusw.sbb.checkin.domain.route.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「집중률 90 이상이면 피한다」 규칙을 언제 주는지 지킨다 (작업지시 4번).
 *
 * <p>값만 실어 보내고 규칙을 안 주면 AI 는 그 숫자를 무시한다. 반대로 붐비는 후보가 하나도
 * 없는데 규칙만 주면 없는 것을 피하라는 말이 된다.
 *
 * <p>제일 중요한 것은 <b>빼라고 하지 않는 것</b>이다. 부산·경주에서 90 이상을 다 빼면
 * 갈 곳이 없다 — 시각을 옮기거나 같은 성격의 다른 곳으로 바꾸라고 해야 한다.
 */
class RouteCrowdRuleTest {

    private static String rule(String candidateLines) {
        try {
            Method m = AiRouteService.class.getDeclaredMethod("crowdRule", CharSequence.class);
            m.setAccessible(true);
            return (String) m.invoke(null, candidateLines);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("crowdRule 을 찾지 못했다", e);
        }
    }

    @Test
    void 붐비는_후보가_있으면_규칙을_준다() {
        String lines = "  1. name=아쿠아리움 | sub=관광지 | 집중률=99(매우 혼잡)\n";

        assertThat(rule(lines)).contains("집중률 90 이상");
    }

    @Test
    void 붐비는_후보가_없으면_규칙이_아예_없다() {
        String lines = "  1. name=송정해수욕장 | sub=관광지 | 집중률=62(정상)\n"
                     + "  2. name=해리단길 | sub=관광지\n";

        assertThat(rule(lines)).isEmpty();
    }

    @Test
    void 정확히_90이면_붐비는_것으로_본다() {
        assertThat(rule("집중률=90(혼잡)")).isNotEmpty();
        assertThat(rule("집중률=89(혼잡)")).isEmpty();
    }

    @Test
    void 빼라고_하지_않는다() {
        // 90 이상을 다 빼면 부산·경주에서 갈 곳이 없다
        String r = rule("집중률=99(매우 혼잡)");

        assertThat(r).contains("시각으로 옮기거나");
        assertThat(r).contains("하루를 비우지는 마세요");
    }

    @Test
    void 집중률이_없는_것을_한적하다고_말하지_않는다() {
        String r = rule("집중률=95(매우 혼잡)");

        assertThat(r).contains("「한적하다」가 아니라 「모른다」");
    }

    @Test
    void 후보_줄이_비어도_터지지_않는다() {
        assertThat(rule("")).isEmpty();
        assertThat(rule("집중률=")).isEmpty();          // 숫자가 안 붙은 꼴
    }
}
