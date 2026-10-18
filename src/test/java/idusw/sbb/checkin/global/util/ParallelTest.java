package idusw.sbb.checkin.global.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 서로 상관없는 외부 호출을 한꺼번에 보내는 도우미. */
class ParallelTest {

    @Test
    void 셋이_동시에_돈다() {
        // 셋이 모두 동시에 들어와야 풀리는 문. 차례로 돌면 1초 기다리다 false 가 된다
        CountDownLatch gate = new CountDownLatch(3);
        Map<String, Boolean> got = Parallel.map(List.of("a", "b", "c"), k -> {
            gate.countDown();
            try { return gate.await(1, TimeUnit.SECONDS); }
            catch (InterruptedException e) { return false; }
        });
        assertEquals(Map.of("a", true, "b", true, "c", true), got);
    }

    @Test
    void 같은_키는_한_번만_부르고_순서를_지킨다() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Integer> got = Parallel.map(List.of("x", "y", "x", "x"), k -> {
            calls.incrementAndGet();
            return k.length();
        });
        assertEquals(2, calls.get());
        assertEquals(List.of("x", "y"), List.copyOf(got.keySet()));
    }

    @Test
    void 하나가_실패해도_나머지는_받고_실패한_키는_null() {
        Map<Integer, Integer> got = Parallel.map(List.of(1, 2, 3), k -> {
            if (k == 2) throw new IllegalStateException("카카오 장애");
            return k * 10;
        });
        assertEquals(10, got.get(1));
        assertTrue(got.containsKey(2));
        assertNull(got.get(2));
        assertEquals(30, got.get(3));
    }

    @Test
    void 안에서_또_불러도_멈추지_않는다() {
        Map<Integer, Integer> got = Parallel.map(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), k ->
                Parallel.map(List.of(k, k + 100), x -> x).size());
        got.values().forEach(v -> assertEquals(2, v));
    }
}
