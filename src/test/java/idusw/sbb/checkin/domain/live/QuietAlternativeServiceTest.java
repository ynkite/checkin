package idusw.sbb.checkin.domain.live;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「다른 곳으로」를 언제 권할지 가르는 자리다.
 *
 * 틀리면 2~3 차이로 다른 동네까지 옮기라고 하거나, 집중률을 모르는 곳을
 * 「여기가 더 한적하다」고 말하게 된다. 둘 다 헛걸음을 만든다.
 */
class QuietAlternativeServiceTest {

    @Test
    void 뚜렷하게_한적해야_권한다() {
        /* 95 -> 62 는 33 차이. 옮길 값어치가 있다 */
        assertThat(QuietAlternativeService.worthMoving(95, 62)).isTrue();
    }

    @Test
    void 차이가_작으면_권하지_않는다() {
        assertThat(QuietAlternativeService.worthMoving(70, 60)).isFalse();
        /* 딱 임계값이면 권한다 */
        assertThat(QuietAlternativeService.worthMoving(70, 55)).isTrue();
    }

    @Test
    void 더_붐비는_곳으로는_보내지_않는다() {
        assertThat(QuietAlternativeService.worthMoving(40, 80)).isFalse();
    }

    @Test
    void 지금_집중률을_모르면_권하지_않는다() {
        /* 모르는 값과 비교해서 「더 한적하다」고 말할 수 없다 */
        assertThat(QuietAlternativeService.worthMoving(null, 10)).isFalse();
    }
}
