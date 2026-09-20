package idusw.sbb.checkin.domain.crowd.check;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「장소 확인하기」를 하루 몇 번까지.
 *
 * <p><b>왜 막나</b> — 이 단추 하나가 SK 지오비전 퍼즐과 한국관광공사 집중률을
 * 부른다. SK 는 유료고, 관광공사는 인증키마다 하루 호출 한도가 있다.
 * 사이트가 공개되어 있으니 한 사람이 눌러 대면 <b>그날 팀 전체의 키가 막힌다.</b>
 * TMAP 에서 이미 겪었다 — 403 한 번에 KeyRing 이 키를 빼서 자차 길찾기까지 멈췄다.
 *
 * <p><b>무엇을 세나</b> — 사람이다. 여행이 아니다.
 * 여행마다 세면 여행을 새로 만들어 얼마든지 늘릴 수 있다.
 *
 * <p><b>언제 풀리나</b> — 자정(한국 시각)이다. 「처음 누른 뒤 24시간」보다
 * 말하기 쉽다. 화면에 「내일 다시 확인할 수 있습니다」라고 쓸 수 있다.
 * 사용자가 머릿속으로 남은 시간을 계산하게 만들지 않는다.
 *
 * <p><b>왜 메모리에만 두나</b> — 서버가 내려가면 횟수가 풀린다. 알고 두는 것이다.
 * 이 문은 작정한 사람을 막으려는 게 아니라 <b>무심코 눌러 대는 것</b>을 막는다.
 * 표를 하나 더 만들고 옮기는 값이 지금 얻는 것보다 크다.
 * 오래 쓸 물건이 되면 그때 옮긴다 — 그때는 이 설명이 근거가 된다.
 *
 * <p>세는 곳은 서버다. 화면에서만 막으면 의미가 없다.
 */
@Component
public class PlaceCheckQuota {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 하루 몇 번. properties 로 바꿀 수 있게 둔다 — 심사 중에 손볼 일이 생길 수 있다 */
    private final int perDay;

    public PlaceCheckQuota(@Value("${checkin.place-check.per-day:3}") int perDay) {
        this.perDay = perDay;
    }

    private record Used(LocalDate day, int count) {}

    private final Map<Long, Used> byUser = new ConcurrentHashMap<>();

    /**
     * @param remaining 이번 것까지 센 뒤 남은 횟수
     * @param resetAt   다시 되는 시각. 화면이 사람 말로 바꿔 쓴다
     */
    public record Verdict(boolean allowed, int remaining, int limit, LocalDateTime resetAt) {}

    /** 한 번 쓴다. 남은 것이 없으면 쓰지 않고 거절한다 */
    public Verdict use(Long userId) {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();

        /* 누군지 모르면 셀 수가 없다. 세지 못하는 것을 통과시키지 않는다 */
        if (userId == null) return new Verdict(false, 0, perDay, tomorrow);

        /* 읽고-판단하고-쓰기를 한 덩어리로 해야 한다. 나눠 하면 같은 사람이
           두 번 동시에 눌렀을 때 둘 다 통과한다. compute 는 열쇠 하나를 잠그고 돈다. */
        boolean[] ok = { false };
        Used after = byUser.compute(userId, (k, old) -> {
            int used = (old == null || !old.day().equals(today)) ? 0 : old.count();
            if (used >= perDay) return new Used(today, used);   /* 꽉 찼다. 안 늘린다 */
            ok[0] = true;
            return new Used(today, used + 1);
        });

        return new Verdict(ok[0], Math.max(0, perDay - after.count()), perDay, tomorrow);
    }

    /** 쓰지 않고 지금 몇 번 남았는지만 본다. 화면에 미리 적어 주려고 */
    public Verdict peek(Long userId) {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        Used u = userId == null ? null : byUser.get(userId);
        int used = (u == null || !u.day().equals(today)) ? 0 : u.count();
        return new Verdict(used < perDay, Math.max(0, perDay - used), perDay, tomorrow);
    }
}
