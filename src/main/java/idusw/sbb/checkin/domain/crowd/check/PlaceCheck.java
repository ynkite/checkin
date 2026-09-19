package idusw.sbb.checkin.domain.crowd.check;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「장소 확인하기」 결과와 판단 규칙.
 *
 * 판단은 여기 한 곳에서 한다(스프링 없음, 시험하기 쉽게).
 * 값을 모으는 일은 {@link PlaceCheckService} 가 한다.
 *
 * 규칙
 *  - 무엇을 근거로 한 숫자인지 늘 적는다(source · sourceLabel). 「지금 붐빔」과 「그날 붐빌 것 같음」은 다른 말이다
 *  - 하나를 강요하지 않는다. 문제가 있으면 갈래를 여럿 주고, 「그대로 가기」도 늘 넣는다
 *  - 문제가 없으면 「그대로 가도 됩니다」라고 말한다. 아무 말 없으면 확인이 된 건지 모른다
 *  - 아무것도 확인하지 못했으면 「괜찮다」가 아니라 「확인하지 못했다」다
 */
public final class PlaceCheck {

    private PlaceCheck() {}

    /** OK 확인됨 · NO_DATA 자료가 없는 곳 · UNAVAILABLE 불렀는데 못 받음 · SKIPPED 볼 필요가 없음 */
    public enum Status { OK, NO_DATA, UNAVAILABLE, SKIPPED }

    public enum Kind { CROWD, RAIN, TRAFFIC }

    /**
     * 확인한 것 하나.
     * @param issue  문제가 있다고 판단했는가
     * @param source 근거의 기계 이름 (SK_LIVE · TOUR_FORECAST · KMA_SHORT · KMA_MID · KMA_NORMAL · TMAP_LIVE · TMAP_PREDICT)
     * @param sourceLabel 화면에 그대로 적는 근거 이름
     * @param summary 한 줄 — 무엇이 어떤가
     */
    public record Finding(Kind kind, Status status, boolean issue, String source, String sourceLabel,
                          String summary, Map<String, Object> values) {}

    public record Option(String key, String label, String note, String method, String endpoint, boolean oneClick) {}

    public record Verdict(String status, String text) {}   // ISSUE · OK · UNKNOWN

    public record Result(String mode, String modeLabel, String date, String checkedAt,
                         Map<String, Object> place, Verdict verdict,
                         List<Finding> findings, List<Option> options) {}

    /** 「이 장소를 확인할 것이 없다」 — 경로가 없거나 남은 일정이 없을 때 */
    public static Result nothing(String mode, String modeLabel, String date, String checkedAt, String why) {
        return new Result(mode, modeLabel, date, checkedAt, Map.of(), new Verdict("UNKNOWN", why), List.of(), List.of());
    }

    /**
     * 확인한 것들로 판단과 갈래를 만든다.
     * @param liveActions 실시간 서버가 이미 준 갈래(key → 한 벌). 끝점·oneClick 을 거기서 가져온다
     */
    public static Result decide(String mode, String modeLabel, String date, String checkedAt,
                                Map<String, Object> place, List<Finding> findings,
                                Map<String, Map<String, Object>> liveActions, Integer lateMin) {
        String name = String.valueOf(place.getOrDefault("name", "다음 장소"));
        boolean today = "LIVE".equals(mode);
        List<Option> opts = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (Finding f : findings) {
            if (!f.issue()) continue;
            switch (f.kind()) {
                case RAIN -> {
                    problems.add(today ? "비 소식이 있습니다" : "그날 비 예보가 있습니다");
                    add(opts, fromLive(liveActions, "indoor", "실내로 바꾸기", "그날 야외 일정을 실내로 다시 짭니다"));
                }
                case CROWD -> {
                    /* 실측과 예측을 같은 말로 쓰지 않는다. 당일이어도 실측을 못 받았으면 예측이다 */
                    problems.add("SK_LIVE".equals(f.source()) ? "지금 붐빕니다"
                            : today ? "오늘 붐빌 것으로 예측됩니다" : "그날 붐빌 것 같습니다");
                    add(opts, fromLive(liveActions, "swap", "순서 바꾸기", "붐비는 곳을 뒤로 미룹니다"));
                    Map<String, Object> q = liveActions.get("quiet");
                    add(opts, new Option("quiet", "한적한 다른 곳",
                            "그 시각에 덜 붐비는 곳을 보여 드립니다. 바꿀지는 고르시면 됩니다",
                            "GET", q != null ? String.valueOf(q.getOrDefault("quietEndpoint", "")) : "", false));
                }
                case TRAFFIC -> {
                    problems.add(lateMin != null ? "지금 가면 " + lateMin + "분 늦습니다" : "가는 길이 오래 걸립니다");
                    add(opts, new Option("leave_now", "바로 출발하기",
                            "지금 출발하면 늦는 시간을 줄일 수 있습니다", null, null, false));
                    add(opts, new Option("other_mode", "다른 이동수단 보기",
                            "대중교통 시간은 여기서 확인하지 않았습니다. 지도 앱에서 비교해 보세요", null, null, false));
                    add(opts, fromLive(liveActions, "navi", "길 안내", "다음 장소까지 안내를 켭니다"));
                }
            }
        }

        boolean anyChecked = findings.stream().anyMatch(f -> f.status() == Status.OK);
        Verdict v;
        if (!problems.isEmpty()) {
            v = new Verdict("ISSUE", name + " — " + String.join(", ", problems) + ". 아래에서 고르시면 됩니다.");
        } else if (anyChecked) {
            v = new Verdict("OK", name + " — 그대로 가도 됩니다. 확인한 것 중에 걸리는 게 없습니다.");
        } else {
            v = new Verdict("UNKNOWN", name + "의 지금 상황을 확인하지 못했습니다. 아래에 이유를 적었습니다.");
        }
        /* 늘 「그대로 가기」를 둔다. 문제가 있어도 가겠다는 선택은 사용자의 것이다 */
        opts.add(new Option("keep", "그대로 가기", problems.isEmpty() ? "계획대로 갑니다" : "알고 그대로 갑니다", null, null, false));
        return new Result(mode, modeLabel, date, checkedAt, place, v, findings, opts);
    }

    private static Option fromLive(Map<String, Map<String, Object>> live, String key, String label, String note) {
        Map<String, Object> a = live.get(key);
        if (a == null) return new Option(key, label, note, null, null, false);
        Object m = a.get("method"), e = a.get("endpoint");
        return new Option(key, String.valueOf(a.getOrDefault("label", label)), String.valueOf(a.getOrDefault("note", note)),
                m == null ? null : String.valueOf(m), e == null ? null : String.valueOf(e),
                Boolean.TRUE.equals(a.get("oneClick")));
    }

    private static void add(List<Option> opts, Option o) {
        if (opts.stream().noneMatch(x -> x.key().equals(o.key()))) opts.add(o);
    }

    static Map<String, Object> values(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
