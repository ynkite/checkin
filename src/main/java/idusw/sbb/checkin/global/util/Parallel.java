package idusw.sbb.checkin.global.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * 서로 기다릴 일 없는 외부 호출(카카오·TMAP·관광공사)을 한꺼번에 보낸다.
 *
 * <p>일정 하나를 저장할 때 좌표·길찾기·이동시간을 장소마다 하나씩 차례로 물어서
 * 수십 번의 왕복이 줄을 섰다. 답은 서로 상관이 없으니 같이 보내고 다 오면 모은다.
 *
 * <p>실패한 키는 값이 null 이다 — 부르는 쪽이 원래 하던 대로 「못 찾음」으로 처리한다.
 * 이 풀 안에서 또 부르면 차례로 돈다. 풀이 자기 일을 기다리며 멈추지 않게 하려는 것이다.
 */
public final class Parallel {

    private static final String NAME = "par-call";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, NAME);
        t.setDaemon(true);
        return t;
    });

    private Parallel() {}

    /** 키마다 f 를 동시에 돌려 키 → 값으로 준다. 같은 키는 한 번만 부른다. 들어온 순서를 지킨다. */
    public static <K, V> Map<K, V> map(Collection<K> keys, Function<K, V> f) {
        Map<K, V> out = new LinkedHashMap<>();
        if (Thread.currentThread().getName().equals(NAME)) {
            for (K k : keys) if (!out.containsKey(k)) out.put(k, safe(f, k));
            return out;
        }
        Map<K, CompletableFuture<V>> jobs = new LinkedHashMap<>();
        for (K k : keys) jobs.computeIfAbsent(k, x -> CompletableFuture.supplyAsync(() -> safe(f, x), POOL));
        jobs.forEach((k, job) -> out.put(k, job.join()));
        return out;
    }

    private static <K, V> V safe(Function<K, V> f, K k) {
        try {
            return f.apply(k);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
