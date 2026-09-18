package idusw.sbb.checkin.domain.live;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「순서 바꾸기」가 무엇을 어디로 옮기는지 가르는 자리다.
 *
 * 틀리면 이미 다녀온 일정이 뒤섞이거나, 붐비는 곳을 그대로 두고
 * 바꿨다고 말한다. 둘 다 사용자가 화면을 못 믿게 만든다.
 */
class LiveReplanTest {

    @Test
    void 붐비는_곳을_뒤로_미룬다() {
        Integer[] crowds = {30, 95, 40, 80, 50};

        assertThat(LiveReplan.order(crowds, 0, LiveReplan.BUSY))
                .containsExactly(0, 2, 4, 1, 3);
    }

    @Test
    void 지나간_곳은_건드리지_않는다() {
        Integer[] crowds = {30, 95, 40, 80, 50};

        /* 0·1 은 이미 다녀왔다. 95 인 1번을 뒤로 보내지 않는다 */
        assertThat(LiveReplan.order(crowds, 2, LiveReplan.BUSY))
                .containsExactly(0, 1, 2, 4, 3);
    }

    @Test
    void 집중률을_모르는_곳은_붐빈다고_보지_않는다() {
        /* 「확인되지 않음」을 근거로 일정을 밀면 안 된다 */
        assertThat(LiveReplan.order(new Integer[]{null, 90, null}, 0, LiveReplan.BUSY))
                .containsExactly(0, 2, 1);
    }

    @Test
    void 한적한_순이면_바꾸지_않는다() {
        int[] order = LiveReplan.order(new Integer[]{10, 20, 30}, 0, LiveReplan.BUSY);

        assertThat(LiveReplan.changed(order)).isFalse();
    }

    @Test
    void 시각이_지난_곳까지가_다녀온_구간이다() {
        String[] times = {"09:10", "12:40", "14:20", "17:40"};

        assertThat(LiveReplan.firstRemaining(times, LocalTime.of(13, 0))).isEqualTo(2);
        assertThat(LiveReplan.firstRemaining(times, LocalTime.of(8, 0))).isZero();
        /* 오늘이 아니면 전부 남은 것으로 본다 */
        assertThat(LiveReplan.firstRemaining(times, null)).isZero();
    }

    @Test
    void 시각을_모르면_지나갔다고_하지_않는다() {
        assertThat(LiveReplan.firstRemaining(new String[]{"", "12:40"}, LocalTime.of(13, 0)))
                .isZero();
    }
}
