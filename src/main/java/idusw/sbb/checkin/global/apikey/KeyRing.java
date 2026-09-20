package idusw.sbb.checkin.global.apikey;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 키 여러 개를 돌려 쓴다.
 *
 * 왜 — TMAP 무료 한도가 하루 단위로 걸린다. 심사 기간에 한 사람 키로
 * 버티다가 한도를 넘기면 그 순간부터 화면이 「연동 전」이 된다.
 * 팀원이 각자 발급받아 쉼표로 이어 붙이면, 한도가 찬 키는 건너뛰고
 * 다음 키로 넘어간다.
 *
 *   tmap.api.key=내키,팀원1키,팀원2키
 *
 * 규칙 —
 *   · 한도 초과(429)나 키 거부(401)를 만나면 그 키를 오늘 안 쓴다
 *   · 403 은 키 문제가 아니다. 아래 fail() 참고
 *   · 날이 바뀌면 전부 되살린다. 한도는 날짜로 초기화된다
 *   · 쓸 키가 하나도 없으면 null 을 돌려준다. 호출부는 「연동 전」으로 답한다
 *
 * 스레드 — 호출이 동시에 들어올 수 있어서 상태 변경만 synchronized 로 묶는다.
 * 읽기는 막지 않는다. 한 번 더 부르는 것이 멈추는 것보다 낫다.
 */
@Slf4j
public class KeyRing {

    private final String name;
    private final List<String> keys = new ArrayList<>();
    private final boolean[] dead;
    private LocalDate day = LocalDate.now();
    private int at = 0;

    public KeyRing(String name, String raw) {
        this.name = name;
        if (raw != null) {
            for (String s : raw.split(",")) {
                String k = s.trim();
                if (!k.isEmpty()) keys.add(k);
            }
        }
        this.dead = new boolean[Math.max(1, keys.size())];
    }

    public boolean ready() {
        return current() != null;
    }

    /** 몇 개를 들고 있나. 화면의 「연동 상태」가 쓴다 */
    public int size() {
        return keys.size();
    }

    /** 지금 쓸 키. 살아 있는 것이 없으면 null */
    public synchronized String current() {
        rollDay();
        if (keys.isEmpty()) return null;
        for (int i = 0; i < keys.size(); i++) {
            int k = (at + i) % keys.size();
            if (!dead[k]) { at = k; return keys.get(k); }
        }
        return null;
    }

    /**
     * 이 키로는 안 된다고 알려 준다. 다음 키로 넘긴다.
     *
     * @param status HTTP 상태. 429·401 만 키 문제로 본다.
     *               500 이나 타임아웃은 상대 서버 사정이라 키를 죽이지 않는다 —
     *               그걸로 죽이면 SK 가 한 번 느릴 때 키를 다 버린다.
     *
     *               403 도 죽이지 않는다. SK 게이트웨이의 403 은 「한도 초과」가 아니라
     *               「이 앱키로는 그 상품을 못 쓴다」는 뜻이다. 상품은 키마다 다르지 않고
     *               우리 키가 다 같은 상품이라, 한 번 403 을 만나면 다음 키도, 그다음 키도
     *               같은 답을 준다. 그런데 그때마다 키를 하나씩 죽이니 대중교통 한 번
     *               불러 본 것으로 그날 자차 경로까지 전부 「연동 전」이 됐다.
     *               안 되는 것은 그 API 하나지 키가 아니다.
     * @return 넘길 키가 남아 있으면 true
     */
    public synchronized boolean fail(int status) {
        rollDay();
        if (keys.isEmpty()) return false;
        if (status != 429 && status != 401) return false;

        dead[at] = true;
        log.warn("[{}] {}번 키를 오늘 쓰지 않습니다 (HTTP {}). 남은 키 {}개",
                name, at + 1, status, alive());
        at = (at + 1) % keys.size();
        return current() != null;
    }

    public synchronized int alive() {
        rollDay();
        int n = 0;
        for (int i = 0; i < keys.size(); i++) if (!dead[i]) n++;
        return n;
    }

    /** 날이 바뀌면 전부 되살린다. 한도는 날짜로 초기화된다 */
    private void rollDay() {
        LocalDate t = LocalDate.now();
        if (!t.equals(day)) {
            day = t;
            for (int i = 0; i < dead.length; i++) dead[i] = false;
            at = 0;
        }
    }

    /** 자체 점검 — 서버를 부르지 않는다 */
    public static void main(String[] args) {
        KeyRing r = new KeyRing("test", " a , b ,, c ");
        assert r.size() == 3 : "빈 칸은 버린다";
        assert "a".equals(r.current());

        assert !r.fail(500) : "서버 오류로는 키를 죽이지 않는다";
        assert "a".equals(r.current()) : "500 이면 같은 키를 그대로 쓴다";

        assert r.fail(429) : "한도가 차면 다음 키로";
        assert "b".equals(r.current());
        assert r.alive() == 2;

        assert !r.fail(403) : "상품을 안 산 것으로는 키를 죽이지 않는다";
        assert "b".equals(r.current()) : "403 이면 같은 키를 그대로 쓴다";
        assert r.alive() == 2 : "403 이 다른 키까지 끌고 죽으면 안 된다";

        assert r.fail(401) : "키가 거부되면 넘긴다";
        assert "c".equals(r.current());

        assert !r.fail(429) : "마지막 키가 죽으면 넘길 곳이 없다";
        assert r.current() == null : "다 죽으면 없다고 말한다";
        assert !r.ready();

        KeyRing e = new KeyRing("empty", "");
        assert e.size() == 0 && !e.ready() && e.current() == null;
        assert !e.fail(429);

        KeyRing n = new KeyRing("null", null);
        assert n.size() == 0 && !n.ready();

        System.out.println("KeyRing 자체 점검 통과");
    }
}
