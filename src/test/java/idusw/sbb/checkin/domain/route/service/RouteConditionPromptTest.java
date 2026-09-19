package idusw.sbb.checkin.domain.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조건(반려동물·유아)과 집중률이 <b>후보 줄과 규칙으로</b> 프롬프트에 실리는지 지킨다.
 *
 * <p>전에는 조건이 「반려동물: O」 한 글자로만 들어가 장소 고르는 데 영향이 없었다.
 * 표시가 줄에서 빠지거나 규칙이 사라지면 그 상태로 조용히 되돌아간다 — 동선은 계속
 * 나오고 테스트도 안 깨지므로 아무도 모른다. 그래서 여기서 지킨다.
 *
 * <p>제일 중요한 것은 <b>「표시 없음」을 「조건 불충족」으로 말하지 않는 것</b>이다.
 * 관광공사 목록은 시군구당 몇 건뿐이라, 표시가 없는 곳이 대부분이다.
 */
class RouteConditionPromptTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /* ── 후보 줄 표시 ───────────────────────────────────────── */

    @Test
    void 조건과_집중률이_후보_줄에_실린다() {
        ObjectNode n = OM.createObjectNode();
        n.put("name", "해운대 해수욕장");
        n.put("petOk", true);
        n.put("crowd", 92);
        n.put("crowdLabel", "매우 혼잡");

        assertThat(flags(n))
                .contains("반려동물동반가능")
                .contains("집중률=92")
                .contains("매우 혼잡");
    }

    @Test
    void 집중률이_없으면_아무_말도_하지_않는다() {
        // 광주·전남은 집중률 데이터가 아예 없다. 0(한적)으로 두면 거짓이 된다
        ObjectNode n = OM.createObjectNode();
        n.put("name", "양림동 근대역사문화마을");

        assertThat(flags(n)).isEmpty();
        assertThat(flags(n)).doesNotContain("집중률");
    }

    @Test
    void 무장애_표시는_따로_붙는다() {
        ObjectNode n = OM.createObjectNode();
        n.put("barrierFree", true);

        assertThat(flags(n)).contains("무장애").doesNotContain("반려동물");
    }

    /* ── 규칙 14 ────────────────────────────────────────────── */

    @Test
    void 조건을_안_고르면_규칙이_아예_없다() {
        assertThat(rule(form(0, 0))).isEmpty();
    }

    @Test
    void 반려동물만_고르면_반려동물_규칙만_나간다() {
        String r = rule(form(1, 0));

        assertThat(r).contains("반려동물동반가능");
        assertThat(r).doesNotContain("유아 동반 여행입니다");
    }

    @Test
    void 표시가_없는_곳을_조건_불충족으로_말하지_않는다() {
        String r = rule(form(1, 1));

        assertThat(r).contains("확인되지 않은 곳");
        assertThat(r).contains("표시된 곳을 빼지 마세요");
    }

    /* ── 바닥 ──────────────────────────────────────────────── */

    private static PlanInputForm form(int pet, int infant) {
        return PlanInputForm.builder().hasPet(pet).hasInfant(infant).build();
    }

    private static String flags(ObjectNode n) {
        return invoke("candidateFlags", ObjectNode.class, n);
    }

    private static String rule(PlanInputForm f) {
        return invoke("conditionRule", PlanInputForm.class, f);
    }

    private static String invoke(String name, Class<?> paramType, Object arg) {
        try {
            Method m = AiRouteService.class.getDeclaredMethod(name, paramType);
            m.setAccessible(true);
            return (String) m.invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(name + " 을 찾지 못했다 — 이름이나 시그니처가 바뀌었다", e);
        }
    }
}
