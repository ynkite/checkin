package idusw.sbb.checkin.domain.weather;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비 예보가 걸린 날에 어느 장소를 실내로 바꿔 권할지 가르는 자리다.
 * 틀리면 비 오는 날 사용자를 해수욕장으로 내보내거나, 박물관을 두고
 * 실내 대안을 권한다.
 */
class PlaceOutdoorTest {

    @Test
    void 이름이_야외면_분류와_무관하게_야외다() {
        assertThat(PlaceOutdoor.is("해운대해수욕장", "tour")).isTrue();
        assertThat(PlaceOutdoor.is("태종대유원지 전망대", "tour")).isTrue();
        assertThat(PlaceOutdoor.is("성산일출봉 오름", "tour")).isTrue();
        assertThat(PlaceOutdoor.is("자갈치시장", "food")).isTrue();
    }

    @Test
    void 이름이_실내면_관광지라도_실내다() {
        /* 이게 깨지면 비 오는 날 미술관을 두고 실내 대안을 권한다. */
        assertThat(PlaceOutdoor.is("부산현대미술관", "tour")).isFalse();
        assertThat(PlaceOutdoor.is("국립해양박물관", "tour")).isFalse();
        assertThat(PlaceOutdoor.is("롯데월드 아쿠아리움", "tour")).isFalse();
        assertThat(PlaceOutdoor.is("영화의전당 극장", "tour")).isFalse();
    }

    @Test
    void 이름에_단서가_없으면_분류로_간다() {
        assertThat(PlaceOutdoor.is("감천문화마을", "tour")).isTrue();
        assertThat(PlaceOutdoor.is("동래할매파전", "food")).isFalse();
        assertThat(PlaceOutdoor.is("이름없는카페", "cafe")).isFalse();
        assertThat(PlaceOutdoor.is("제주신라호텔", "stay")).isFalse();
    }

    @Test
    void 야외_낱말이_실내_낱말보다_먼저_이긴다() {
        /* 「해변공원 전시관」처럼 둘이 섞이면 야외로 둔다. 애매할 때
           실내로 잘못 보면 비 오는 날 그대로 내보내기 때문이다. */
        assertThat(PlaceOutdoor.is("해변 전시관", "tour")).isTrue();
    }

    @Test
    void 이름이_없어도_터지지_않는다() {
        assertThat(PlaceOutdoor.is(null, "tour")).isTrue();
        assertThat(PlaceOutdoor.is(null, "food")).isFalse();
        assertThat(PlaceOutdoor.is("", null)).isFalse();
    }
}
