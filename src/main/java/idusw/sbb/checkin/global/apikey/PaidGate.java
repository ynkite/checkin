package idusw.sbb.checkin.global.apikey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 돈이 드는 호출을 막는 문.
 *
 * 왜 — 지오비전 퍼즐 상품은 무료가 아니다. 개발하면서 화면을 열 때마다
 * 호출되면 심사 전에 예산이 녹는다. 그래서 기본은 닫아 둔다.
 * 최종 테스트 때만 열고, 열어 둔 동안에도 하루 상한을 둔다.
 *
 *   sk.paid.enabled=false        기본. 닫혀 있다
 *   sk.paid.daily-cap=200        열었을 때 하루 몇 번까지
 *
 * 닫혀 있으면 클라이언트가 호출을 아예 안 하고, 화면은
 * 「아직 연동 전입니다」로 둔다 — 0 으로 채우지 않는다.
 *
 * 상한을 넘기면 그날은 더 안 부른다. 상한은 날짜로 초기화된다.
 * 무료 상품(TMAP · 관광공사 · 지하철 혼잡도)은 이 문을 지나지 않는다.
 */
@Slf4j
@Component
public class PaidGate {

    @Value("${sk.paid.enabled:false}")
    private boolean enabled;

    @Value("${sk.paid.daily-cap:200}")
    private int cap;

    private final Map<String, AtomicInteger> used = new ConcurrentHashMap<>();
    private volatile LocalDate day = LocalDate.now();

    /** 열려 있고 상한 안이면 참. 부르기 직전에 물어본다 */
    public boolean allow(String what) {
        if (!enabled) return false;
        rollDay();
        int n = used.computeIfAbsent(what, k -> new AtomicInteger()).incrementAndGet();
        if (n > cap) {
            if (n == cap + 1) {
                log.warn("[유료] {} 오늘 상한 {}회를 넘겼습니다. 오늘은 더 부르지 않습니다.", what, cap);
            }
            return false;
        }
        return true;
    }

    public boolean enabled() { return enabled; }

    public int cap() { return cap; }

    /** 오늘 몇 번 썼나. 「연동 상태」 화면이 보여 준다 */
    public int usedToday(String what) {
        rollDay();
        AtomicInteger a = used.get(what);
        return a == null ? 0 : Math.min(a.get(), cap);
    }

    private void rollDay() {
        LocalDate t = LocalDate.now();
        if (!t.equals(day)) {
            synchronized (this) {
                if (!t.equals(day)) { day = t; used.clear(); }
            }
        }
    }
}
