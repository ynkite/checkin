package idusw.sbb.checkin.domain.weather;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WeatherRegion} 이 지키는 약속은 하나다 —
 * <b>모르는 여행지에는 지역 이름을 주지 않는다.</b>
 *
 * <p>왜 이걸 테스트로 못 박는가 — {@code WeatherServiceImpl} 의 격자표는
 * {@code getOrDefault(region, 서울)} 이다. 모르는 이름이 오면 조용히 서울 격자를
 * 조회한다. 동선에 날씨를 박는 자리에서 그게 통과하면 「여수 여행인데 서울 날씨」가
 * 실제 예보처럼 DB 에 저장되고, 화면은 그걸 근거로 실내 대안을 권한다.
 * null 이 와야 부르는 쪽이 칸을 비워 둔다.
 */
class WeatherRegionTest {

    @Test
    void 시도만_와도_예보_지역을_찾는다() {
        assertThat(WeatherRegion.of("부산")).isEqualTo("부산");
        assertThat(WeatherRegion.of("제주")).isEqualTo("제주");
        assertThat(WeatherRegion.of("서울")).isEqualTo("서울");
    }

    @Test
    void 시군구까지_와도_시도로_줄여_찾는다() {
        assertThat(WeatherRegion.of("부산 해운대구")).isEqualTo("부산");
        assertThat(WeatherRegion.of("제주도")).isEqualTo("제주");
    }

    @Test
    void 예보_지역_이름이_시도와_다른_곳도_찾는다() {
        /* 경기·전북·강원은 격자표에 시도 이름이 없다. 대표 도시로 조회한다. */
        assertThat(WeatherRegion.of("경기")).isEqualTo("수원");
        assertThat(WeatherRegion.of("전북")).isEqualTo("전주");
        assertThat(WeatherRegion.of("강원")).isEqualTo("강릉");
    }

    @Test
    void 경기_광주시를_전라도_광주로_잡지_않는다() {
        assertThat(WeatherRegion.of("경기 광주시")).isEqualTo("수원");
        assertThat(WeatherRegion.of("광주")).isEqualTo("광주");
    }

    @Test
    void 격자가_없는_여행지에는_지역을_주지_않는다() {
        /* 이게 깨지면 그 지역 동선에 서울 날씨가 실제 예보처럼 저장된다. */
        assertThat(WeatherRegion.of("여수시")).isNull();
        assertThat(WeatherRegion.of("경주시")).isNull();
        assertThat(WeatherRegion.of("울산")).isNull();
        assertThat(WeatherRegion.of("세종")).isNull();
    }

    @Test
    void 빈_값에는_지역을_주지_않는다() {
        assertThat(WeatherRegion.of(null)).isNull();
        assertThat(WeatherRegion.of("")).isNull();
        assertThat(WeatherRegion.of("   ")).isNull();
        assertThat(WeatherRegion.of("있지도 않은 곳")).isNull();
    }
}
