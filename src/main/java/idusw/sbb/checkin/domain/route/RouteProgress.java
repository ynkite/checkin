package idusw.sbb.checkin.domain.route;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 일정을 만드는 동안 어디까지 왔는지.
 *
 * <p>왜 필요한가 — 일정 하나를 만드는 데 30초 넘게 걸린다. 그동안 화면은 덮개를
 * 씌우고 「일정을 만드는 중입니다」만 보여 준다. 사용자는 멈춘 것인지 도는 것인지
 * 알 수 없고 다른 것도 못 한다.
 *
 * <p><b>남은 시간을 지어내지 않는다.</b> 실제로 끝난 단계만 말한다.
 * 백분율도 단계에 매인 값이지 시계로 재는 값이 아니다 — 카카오와 모델이 얼마나
 * 걸릴지는 미리 알 수 없다. 모르는 것을 아는 척하지 않는다.
 *
 * <p>메모리에만 둔다. 서버가 내려가면 사라진다. 그래도 된다 — 만들던 일정도
 * 같이 끊기기 때문이다. 끝난 지 오래된 것은 물어볼 때 걷어낸다.
 */
@Component
public class RouteProgress {

    /** 단계와 그 단계가 끝났을 때의 백분율. 순서가 곧 진행 순서다. */
    public enum Phase {
        QUEUED("기다리는 중입니다", 5),
        COLLECTING("갈 만한 곳을 찾는 중입니다", 45),
        ASSEMBLING("하루 순서를 짜는 중입니다", 70),
        SAVING("일정을 저장하는 중입니다", 85),
        POLISHING("동선을 다듬는 중입니다", 95),
        DONE("다 됐습니다", 100),
        FAILED("만들지 못했습니다", 100);

        private final String message;
        private final int percent;

        Phase(String message, int percent) {
            this.message = message;
            this.percent = percent;
        }

        public String message() { return message; }
        public int percent()    { return percent; }
    }

    private record Entry(Phase phase, String detail, Instant at) {}

    /** 끝난 지 이만큼 지난 것은 물어볼 때 걷어낸다 */
    private static final long KEEP_SECONDS = 600;

    private final Map<Long, Entry> byTrip = new ConcurrentHashMap<>();

    public void set(Long tripId, Phase phase) {
        set(tripId, phase, null);
    }

    public void set(Long tripId, Phase phase, String detail) {
        if (tripId == null) return;
        byTrip.put(tripId, new Entry(phase, detail, Instant.now()));
        sweep();
    }

    /**
     * 지금 어디까지 왔나. 물어본 적 없는 일정이면 비어 있는 답을 준다 —
     * 「모른다」와 「아직 시작 안 했다」를 화면이 구분할 수 있어야 한다.
     */
    public Map<String, Object> of(Long tripId) {
        Entry e = tripId == null ? null : byTrip.get(tripId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (e == null) {
            out.put("known", false);
            return out;
        }
        out.put("known", true);
        out.put("phase", e.phase().name());
        out.put("percent", e.phase().percent());
        out.put("message", e.phase().message());
        out.put("done", e.phase() == Phase.DONE);
        out.put("failed", e.phase() == Phase.FAILED);
        if (e.detail() != null && !e.detail().isBlank()) out.put("detail", e.detail());
        return out;
    }

    public void clear(Long tripId) {
        if (tripId != null) byTrip.remove(tripId);
    }

    private void sweep() {
        Instant cut = Instant.now().minusSeconds(KEEP_SECONDS);
        byTrip.entrySet().removeIf(en -> en.getValue().at().isBefore(cut));
    }
}
