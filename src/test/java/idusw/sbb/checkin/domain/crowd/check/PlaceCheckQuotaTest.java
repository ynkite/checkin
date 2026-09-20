package idusw.sbb.checkin.domain.crowd.check;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「장소 확인하기」 하루 제한. 이 단추가 유료 API 와 관광공사 집중률을 부른다.
 * 한 사람이 눌러 대면 그날 팀 전체의 키가 막힌다.
 */
class PlaceCheckQuotaTest {

    @Test
    void 세_번까지_되고_네_번째는_막힌다() {
        PlaceCheckQuota q = new PlaceCheckQuota(3);
        assertThat(q.use(7L).allowed()).isTrue();
        assertThat(q.use(7L).allowed()).isTrue();
        var third = q.use(7L);
        assertThat(third.allowed()).isTrue();
        assertThat(third.remaining()).isZero();

        var fourth = q.use(7L);
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.remaining()).isZero();
    }

    @Test
    void 사람마다_따로_센다() {
        PlaceCheckQuota q = new PlaceCheckQuota(1);
        assertThat(q.use(1L).allowed()).isTrue();
        assertThat(q.use(1L).allowed()).isFalse();
        /* 다른 사람은 남의 횟수에 영향받지 않는다 */
        assertThat(q.use(2L).allowed()).isTrue();
    }

    @Test
    void 누군지_모르면_통과시키지_않는다() {
        /* 셀 수 없는 것을 통과시키면 세는 의미가 없다 */
        assertThat(new PlaceCheckQuota(3).use(null).allowed()).isFalse();
    }

    @Test
    void 보기만_할_때는_횟수를_쓰지_않는다() {
        PlaceCheckQuota q = new PlaceCheckQuota(3);
        assertThat(q.peek(5L).remaining()).isEqualTo(3);
        assertThat(q.peek(5L).remaining()).isEqualTo(3);   /* 봐도 안 줄어든다 */
        q.use(5L);
        assertThat(q.peek(5L).remaining()).isEqualTo(2);
    }

    @Test
    void 막혔을_때도_언제_풀리는지_말한다() {
        PlaceCheckQuota q = new PlaceCheckQuota(1);
        q.use(9L);
        var v = q.use(9L);
        assertThat(v.allowed()).isFalse();
        /* 「잠시 후」가 아니라 실제 시각이 있어야 화면이 사람 말로 바꿔 쓸 수 있다 */
        assertThat(v.resetAt()).isNotNull();
        assertThat(v.resetAt().toLocalTime().toString()).isEqualTo("00:00");
        assertThat(v.limit()).isEqualTo(1);
    }

    @Test
    void 동시에_눌러도_한도를_넘지_않는다() throws Exception {
        /* 읽고-판단하고-쓰기를 나눠 하면 둘 다 통과한다. 실제로 동시에 눌러 본다 */
        PlaceCheckQuota q = new PlaceCheckQuota(5);
        int threads = 16;
        var start = new java.util.concurrent.CountDownLatch(1);
        var done = new java.util.concurrent.CountDownLatch(threads);
        var passed = new java.util.concurrent.atomic.AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try { start.await(); if (q.use(42L).allowed()) passed.incrementAndGet(); }
                catch (InterruptedException ignored) { }
                finally { done.countDown(); }
            }).start();
        }
        start.countDown();
        done.await();
        assertThat(passed.get()).isEqualTo(5);
    }
}
