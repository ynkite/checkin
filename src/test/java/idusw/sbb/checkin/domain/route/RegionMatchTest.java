package idusw.sbb.checkin.domain.route;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 판정이 틀리면 후보가 한 개도 안 남아 일정이 통째로 비었다.
 * 실제로 세종 여행에서 일어났다.
 */
class RegionMatchTest {

    @Test
    void 세종은_부르는_말과_주소에_적히는_말이_다르다() {
        /* 이게 깨지면 세종으로는 일정을 아예 못 만든다. */
        assertThat(RegionMatch.matches("세종시",
                "세종특별자치시 연기면 보통리 183-2",
                "세종특별자치시 연기면 보통3길 31-18")).isTrue();
    }

    @Test
    void 특별자치도도_같은_모양이다() {
        assertThat(RegionMatch.matches("강원도", "강원특별자치도 강릉시 초당동", "")).isTrue();
        assertThat(RegionMatch.matches("전북", "전북특별자치도 전주시 완산구", "")).isTrue();
        assertThat(RegionMatch.matches("제주도", "제주특별자치도 제주시 애월읍", "")).isTrue();
    }

    @Test
    void 이름이_그대로_들어_있으면_당연히_맞다() {
        assertThat(RegionMatch.matches("해운대구", "부산 해운대구 우동", "")).isTrue();
        assertThat(RegionMatch.matches("해운대구", "", "부산 해운대구 해운대해변로 264")).isTrue();
    }

    @Test
    void 다른_지역은_걸러진다() {
        assertThat(RegionMatch.matches("해운대구", "서울 강남구 역삼동", "")).isFalse();
        assertThat(RegionMatch.matches("세종시", "서울 중구 태평로", "")).isFalse();
        assertThat(RegionMatch.matches("전주시", "부산 해운대구 우동", "")).isFalse();
    }

    @Test
    void 시군구를_안_고르면_거르지_않는다() {
        assertThat(RegionMatch.matches(null, "어디든", "")).isTrue();
        assertThat(RegionMatch.matches("", "어디든", "")).isTrue();
        assertThat(RegionMatch.matches("   ", "어디든", "")).isTrue();
    }

    @Test
    void 꼬리말을_뗀_뼈대() {
        assertThat(RegionMatch.core("세종시")).isEqualTo("세종");
        assertThat(RegionMatch.core("세종특별자치시")).isEqualTo("세종");
        assertThat(RegionMatch.core("강원특별자치도")).isEqualTo("강원");
        assertThat(RegionMatch.core("해운대구")).isEqualTo("해운대");
        assertThat(RegionMatch.core("기장군")).isEqualTo("기장");
        assertThat(RegionMatch.core("부산광역시")).isEqualTo("부산");
    }

    @Test
    void 뗐더니_아무것도_안_남으면_원래대로_둔다() {
        /* 「시」 하나만 온 경우에 빈 글자로 만들면 아무것도 못 거른다. */
        assertThat(RegionMatch.core("시")).isEqualTo("시");
        assertThat(RegionMatch.core("구")).isEqualTo("구");
        assertThat(RegionMatch.core(null)).isEmpty();
    }

    @Test
    void 행정구역_이름이_바뀌어도_시도로_맞춘다() {
        /* 인천 중구가 제물포구가 됐다. 시군구로는 한 건도 못 맞춘다.
           이게 깨지면 그 지역 숙소가 전부 탈락해 일정이 비었다. */
        assertThat(RegionMatch.matches("인천 중구",
                "인천 제물포구 항동3가 5", "인천 제물포구 제물량로 217")).isTrue();
    }

    @Test
    void 여행지_전체가_와도_맞는다() {
        assertThat(RegionMatch.matches("부산 해운대구", "부산 해운대구 우동", "")).isTrue();
        assertThat(RegionMatch.matches("세종 세종시", "세종특별자치시 연기면 보통리", "")).isTrue();
        assertThat(RegionMatch.matches("강원 강릉시", "강원특별자치도 강릉시 초당동", "")).isTrue();
    }

    @Test
    void 다른_시도는_여전히_걸러진다() {
        /* 느슨하게 했다고 아무거나 받으면 안 된다. */
        assertThat(RegionMatch.matches("부산 해운대구", "서울 강남구 역삼동", "")).isFalse();
        assertThat(RegionMatch.matches("제주 제주시", "경기 수원시 팔달구", "")).isFalse();
    }
}
