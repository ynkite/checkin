package idusw.sbb.checkin.domain.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 붐비는 곳을 뒤로 미루는 재정렬.
 *
 * <p>왜 필요한가 — 실시간 화면의 「순서 바꾸기」가 눌러도 아무 일이 없었다.
 * 무엇을 어떻게 바꿀지 정하는 자리가 없어서다.
 *
 * <p>규칙은 하나다. <b>지나간 곳은 건드리지 않고</b>, 남은 곳 중 집중률이
 * 임계 이상인 곳을 남은 구간의 뒤로 보낸다. 나머지 순서는 그대로 둔다
 * (안정 정렬). 시각은 자리에 남는다 — 14:20 에 가기로 한 슬롯은 그대로고
 * 그 자리에 들어가는 장소만 바뀐다.
 *
 * <p>집중률을 못 받은 곳은 붐빈다고 보지 않는다. 「확인되지 않음」과
 * 「한적함」은 다르지만, 모르는 것을 근거로 일정을 밀 수는 없다.
 */
public final class LiveReplan {

    private LiveReplan() {}

    /** 이 집중률부터 붐빈다고 본다. 화면의 headline·actions 와 같은 값이다 */
    public static final int BUSY = 70;

    /**
     * 남은 구간을 「한적한 곳 먼저, 붐비는 곳 나중」으로 다시 세운다.
     *
     * @param crowds 정거장별 집중률. 모르는 곳은 null
     * @param from   여기서부터가 남은 구간. 앞은 이미 지나갔다
     * @return 새 순서의 원래 자리 번호. 길이는 crowds 와 같다
     */
    public static int[] order(Integer[] crowds, int from, int threshold) {
        int n = crowds.length;
        int start = Math.max(0, Math.min(from, n));

        List<Integer> quiet = new ArrayList<>();
        List<Integer> busy = new ArrayList<>();
        for (int i = start; i < n; i++) {
            Integer c = crowds[i];
            if (c != null && c >= threshold) busy.add(i); else quiet.add(i);
        }

        int[] out = new int[n];
        for (int i = 0; i < start; i++) out[i] = i;          /* 지나간 곳은 그대로 */
        int k = start;
        for (int i : quiet) out[k++] = i;
        for (int i : busy)  out[k++] = i;
        return out;
    }

    /** 바뀐 게 있는가. 없으면 저장하지 않는다 — 같은 값을 다시 쓰면 이력만 늘어난다 */
    public static boolean changed(int[] order) {
        for (int i = 0; i < order.length; i++) if (order[i] != i) return true;
        return false;
    }

    /**
     * 하루치 places 를 새 순서로 다시 만든다.
     *
     * <p>시각(`time`)은 자리에 남긴다. 그리고 구간 노드(`transit`)는 뺀다 —
     * 순서가 바뀌면 「해운대 -> 남포동 35분」이 더는 맞지 않는다.
     * 저장할 때 다시 채워진다.
     */
    public static ArrayNode rebuild(ArrayNode places, int[] order, List<Integer> stopPositions) {
        List<String> times = new ArrayList<>();
        for (int pos : stopPositions) times.add(places.get(pos).path("time").asText(""));

        ArrayNode out = places.arrayNode();
        for (int i = 0; i < order.length; i++) {
            JsonNode src = places.get(stopPositions.get(order[i]));
            ObjectNode copy = src.deepCopy();
            String time = times.get(i);
            if (!time.isBlank()) copy.put("time", time);
            out.add(copy);
        }
        return out;
    }

    /**
     * 남은 구간이 어디서 시작하는가. 시각이 지난 곳은 이미 다녀온 것으로 본다.
     *
     * @param times 정거장별 시각 「14:20」. 비었거나 모양이 다르면 지나지 않은 것으로 둔다
     * @param now   기준 시각. 오늘이 아니면 null 을 주고, 그때는 0(전부 남음)이다
     */
    public static int firstRemaining(String[] times, java.time.LocalTime now) {
        if (now == null) return 0;
        int i = 0;
        while (i < times.length) {
            java.time.LocalTime t = parse(times[i]);
            if (t == null || !t.isBefore(now)) break;
            i++;
        }
        return i;
    }

    private static java.time.LocalTime parse(String hhmm) {
        if (hhmm == null) return null;
        String s = hhmm.trim();
        int c = s.indexOf(':');
        if (c < 1) return null;
        try {
            int h = Integer.parseInt(s.substring(0, c).trim());
            int m = Integer.parseInt(s.substring(c + 1).trim().substring(0, 2));
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return java.time.LocalTime.of(h, m);
        } catch (Exception e) {
            return null;
        }
    }

    /** 돌려 볼 수 있는 점검 — 분기가 있는 자리라 하나 남긴다 */
    public static void main(String[] args) {
        Integer[] c = {30, 95, 40, 80, 50};

        int[] all = order(c, 0, BUSY);
        assert java.util.Arrays.equals(all, new int[]{0, 2, 4, 1, 3})
                : "붐비는 곳(1·3)이 뒤로: " + java.util.Arrays.toString(all);

        int[] mid = order(c, 2, BUSY);
        assert java.util.Arrays.equals(mid, new int[]{0, 1, 2, 4, 3})
                : "지나간 0·1 은 그대로: " + java.util.Arrays.toString(mid);

        Integer[] unknown = {null, 90, null};
        assert java.util.Arrays.equals(order(unknown, 0, BUSY), new int[]{0, 2, 1})
                : "모르는 곳은 붐빈다고 보지 않는다";

        assert !changed(order(new Integer[]{10, 20, 30}, 0, BUSY)) : "다 한적하면 안 바뀐다";
        assert changed(all) : "바뀐 것을 바뀌었다고 해야 한다";

        String[] times = {"09:10", "12:40", "14:20", "17:40"};
        assert firstRemaining(times, java.time.LocalTime.of(13, 0)) == 2 : "13시면 앞의 둘은 지나갔다";
        assert firstRemaining(times, null) == 0 : "오늘이 아니면 전부 남은 것으로 본다";
        assert firstRemaining(times, java.time.LocalTime.of(8, 0)) == 0 : "이른 아침이면 전부 남는다";
        assert firstRemaining(new String[]{"", "12:40"}, java.time.LocalTime.of(13, 0)) == 0
                : "시각을 모르면 지나갔다고 하지 않는다";

        System.out.println("OK LiveReplan 점검 통과");
    }
}
