package idusw.sbb.checkin.domain.live;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 실시간 화면의 「다음」은 시각이 정한다. 좌표가 시각을 앞질러 건너뛰게 하지 않는다. */
class LiveIndexTest {

    private static final List<Map<String, Object>> DAY = List.of(
            Map.of("name", "부산역", "time", "09:10", "lat", 35.1151, "lng", 129.0422),
            Map.of("name", "감천문화마을", "time", "10:40", "lat", 35.0975, "lng", 129.0106),
            Map.of("name", "자갈치 시장", "time", "12:40", "lat", 35.0966, "lng", 129.0306),
            Map.of("name", "광안리 해수욕장", "time", "14:20", "lat", 35.1532, "lng", 129.1186),
            Map.of("name", "해운대 해수욕장", "time", "17:40", "lat", 35.1587, "lng", 129.1604));

    @Test
    void 새벽이면_첫_일정_전이다() {
        assertThat(LiveService.liveIndex(DAY, LocalTime.of(3, 0), null, null)).isEqualTo(-1);
    }

    @Test
    void 새벽에_광안리_근처에_있어도_건너뛰지_않는다() {
        /* 센텀 근처. 광안리(3번)가 2km 안이지만 시각은 첫 일정 전이다 */
        assertThat(LiveService.liveIndex(DAY, LocalTime.of(3, 0), 35.1600, 129.1300)).isEqualTo(-1);
    }

    @Test
    void 좌표는_시각보다_한_칸_앞까지는_믿는다() {
        /* 12시 반, 아직 감천 시각이지만 이미 자갈치에 와 있다 */
        assertThat(LiveService.liveIndex(DAY, LocalTime.of(12, 30), 35.0966, 129.0306)).isEqualTo(2);
    }

    @Test
    void 좌표가_없으면_시각으로만_가른다() {
        assertThat(LiveService.liveIndex(DAY, LocalTime.of(15, 0), null, null)).isEqualTo(3);
    }
}
