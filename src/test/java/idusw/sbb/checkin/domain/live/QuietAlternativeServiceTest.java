package idusw.sbb.checkin.domain.live;

import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.tour.dto.Spot;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private static CrowdForecast f(String name, Double rate) {
        return new CrowdForecast(name, "20261019", rate, null, null, "TOUR", null, null, null);
    }

    @Test
    void 연관_관광지는_한적한_순서_안에_섞인다() {
        List<CrowdForecast> quiet = List.of(f("A", 10.0), f("B", 40.0));
        List<CrowdForecast> related = List.of(f("C", 25.0), f("B", 40.0), f("D", null));

        /* 같은 곳은 한 번만, 집중률을 모르는 곳은 빠지고, 순서는 한적한 순 그대로 */
        assertThat(QuietAlternativeService.mix(quiet, related))
                .extracting(CrowdForecast::placeName).containsExactly("A", "C", "B");
    }

    @Test
    void 연관_관광지가_없으면_원래_목록_그대로다() {
        List<CrowdForecast> quiet = List.of(f("A", 10.0), f("B", 40.0));
        assertThat(QuietAlternativeService.mix(quiet, List.of())).isEqualTo(quiet);
    }

    private static Spot spot(double lat, double lng) {
        return new Spot("1", "송정역", lat, lng, null, null, null, null, null);
    }

    @Test
    void 이름이_같아도_멀리_떨어진_곳은_버린다() {
        GeoPoint aquarium = new GeoPoint(35.159, 129.160);              /* 해운대 */
        assertThat(QuietAlternativeService.isNear(spot(35.137, 126.790), aquarium)).isFalse();  /* 광주 송정역 */
        assertThat(QuietAlternativeService.isNear(spot(35.178, 129.200), aquarium)).isTrue();   /* 부산 송정역 */
    }

    @Test
    void 좌표가_없으면_버리고_기준점을_모르면_거리는_보지_않는다() {
        assertThat(QuietAlternativeService.isNear(spot(0, 0), null)).isFalse();
        assertThat(QuietAlternativeService.isNear(spot(35.137, 126.790), null)).isTrue();
    }
}
