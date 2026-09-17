package idusw.sbb.checkin.domain.live;

import java.util.List;
import java.util.Map;

/**
 * 「지금 어떤가」 한 벌.
 *
 * 운전 중에 읽는 화면이라 한 번에 한 가지만 말한다.
 * headline 은 소리로 읽어도 되는 한 문장이다.
 */
public record LiveSnapshot(
        Long tripId,
        String title,
        String destination,
        String date,          // 기준 날짜 yyyy-MM-dd
        int dayNo,            // 며칠째. 여행 전이면 0
        String phase,         // BEFORE | TODAY | AFTER
        String headline,      // 한 문장. 음성으로 읽어도 되는 길이

        Map<String, Object> here,     // 지금 있는 곳(또는 직전 정거장)
        Map<String, Object> next,     // 다음 정거장 + 남은 시간
        List<Map<String, Object>> stops,   // 그날 전체. 지난 곳 표시 포함

        Map<String, Object> crowd,    // 다음 정거장 혼잡도
        Map<String, Object> weather,  // 그 시각 날씨
        List<Map<String, Object>> actions  // 지금 할 수 있는 것
) {
    public static LiveSnapshot empty(Long tripId, String why) {
        return new LiveSnapshot(tripId, null, null, null, 0, "BEFORE", why,
                Map.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of());
    }
}
