package idusw.sbb.checkin.domain.crowd;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「자료가 아예 없는 지역」과 「그 장소가 대상이 아님」은 다른 말이다.
 * 광주(29)·전남(46)은 집중률 자료가 없다 — 빈 배열만 주면 화면이 0(한적)으로 읽는다.
 */
class CrowdCoverageTest {

    private final CrowdController controller = new CrowdController(null, null);

    @Test
    void 광주와_전남은_예측_대상이_아니라고_말한다() {
        for (String region : new String[]{"광주", "여수", "순천", "목포"}) {
            CrowdController.Coverage c = controller.coverage(region).getBody().getData();
            assertThat(c.covered()).as(region).isFalse();
            assertThat(c.note()).contains("대상이 아닙니다");
        }
    }

    @Test
    void 자료가_있는_지역은_있다고_말한다() {
        for (String region : new String[]{"부산", "경주", "강릉", "전주"}) {
            CrowdController.Coverage c = controller.coverage(region).getBody().getData();
            assertThat(c.covered()).as(region).isTrue();
            assertThat(c.areaCd()).isNotNull();
        }
    }

    @Test
    void 모르는_지역_이름은_모른다고_말한다() {
        CrowdController.Coverage c = controller.coverage("없는동네123").getBody().getData();
        assertThat(c.covered()).isFalse();
        assertThat(c.areaCd()).isNull();
        assertThat(c.note()).contains("알아보지 못했습니다");
    }
}
