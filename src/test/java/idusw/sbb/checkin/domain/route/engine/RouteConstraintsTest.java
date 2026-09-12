package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RouteConstraintsTest {

    private static final GeoPoint SEOUL_STATION = new GeoPoint(37.5547, 126.9707);
    private static final GeoPoint BUSAN_STATION = new GeoPoint(35.1152, 129.0415);

    private RouteConstraints constraints(GeoPoint arrival, GeoPoint departure) {
        return new RouteConstraints(null, null, null, null, null, null, null, arrival, departure, null, null);
    }

    @Test
    void 도착지점이_없으면_예외() {
        assertThatThrownBy(() -> constraints(null, BUSAN_STATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 귀가지점을_안주면_도착지점과_같다() {
        RouteConstraints c = constraints(SEOUL_STATION, null);
        assertThat(c.departurePoint()).isEqualTo(SEOUL_STATION);
    }

    @Test
    void 귀가지점을_주면_그대로_쓴다() {
        RouteConstraints c = constraints(SEOUL_STATION, BUSAN_STATION);
        assertThat(c.departurePoint()).isEqualTo(BUSAN_STATION);
    }

    @Test
    void 점심시간대_시작이_종료보다_늦으면_예외() {
        assertThatThrownBy(() -> new RouteConstraints(
                null, null,
                LocalTime.of(14, 0), LocalTime.of(11, 0),
                null, null, null,
                SEOUL_STATION, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 저녁시간대_시작과_종료가_같으면_예외() {
        assertThatThrownBy(() -> new RouteConstraints(
                null, null,
                null, null,
                LocalTime.of(18, 0), LocalTime.of(18, 0), null,
                SEOUL_STATION, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 하루_최대_이동시간이_음수면_예외() {
        assertThatThrownBy(() -> new RouteConstraints(
                null, null, null, null, null, null,
                Duration.ofMinutes(-1),
                SEOUL_STATION, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 시간대가_없으면_항상_그_시간대_안이다() {
        RouteConstraints c = constraints(SEOUL_STATION, null);

        assertThat(c.isWithinLunchWindow(LocalTime.of(3, 0))).isTrue();
        assertThat(c.isWithinDinnerWindow(LocalTime.of(3, 0))).isTrue();
    }

    @Test
    void 점심시간대_경계값_판정() {
        RouteConstraints c = new RouteConstraints(
                null, null,
                LocalTime.of(11, 30), LocalTime.of(13, 30),
                null, null, null,
                SEOUL_STATION, null, null, null);

        assertThat(c.isWithinLunchWindow(LocalTime.of(11, 29))).isFalse();
        assertThat(c.isWithinLunchWindow(LocalTime.of(11, 30))).isTrue();
        assertThat(c.isWithinLunchWindow(LocalTime.of(13, 30))).isTrue();
        assertThat(c.isWithinLunchWindow(LocalTime.of(13, 31))).isFalse();
    }

    @Test
    void 귀가지점_자체는_우회비용이_0이다() {
        RouteConstraints c = constraints(SEOUL_STATION, BUSAN_STATION);
        assertThat(c.detourCostKm(BUSAN_STATION)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void 귀가지점_기본값일때_우회비용은_왕복거리의_2배다() {
        RouteConstraints c = constraints(SEOUL_STATION, null);
        GeoPoint candidate = new GeoPoint(36.5, 127.9); // 대전 근방, 임의 후보

        double expected = 2 * SEOUL_STATION.distanceKmTo(candidate);
        assertThat(c.detourCostKm(candidate)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void 우회비용은_음수가_되지_않는다() {
        RouteConstraints c = constraints(SEOUL_STATION, BUSAN_STATION);
        GeoPoint anywhere = new GeoPoint(37.0, 128.5);

        assertThat(c.detourCostKm(anywhere)).isGreaterThanOrEqualTo(0.0);
    }

    // ── 결정 7-1 : dayStartTime/dayEndTime 기본값과 첫날/마지막날 특례 ──────

    @Test
    void dayStart와_dayEnd의_기본값은_9시와_21시다() {
        RouteConstraints c = constraints(SEOUL_STATION, null);

        assertThat(c.dayStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(c.dayEndTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void dayStart가_dayEnd보다_늦거나_같으면_예외() {
        assertThatThrownBy(() -> new RouteConstraints(
                null, null, null, null, null, null, null,
                SEOUL_STATION, null,
                LocalTime.of(21, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 첫날이_아니면_새벽_도착이어도_dayStartTime_그대로다() {
        RouteConstraints c = new RouteConstraints(
                LocalDateTime.of(2026, 9, 21, 6, 0), null, null, null, null, null, null,
                SEOUL_STATION, null, null, null);

        assertThat(c.effectiveDayStart(false)).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void 첫날_새벽_도착이면_dayStartTime보다_이르게_당겨지지_않는다() {
        RouteConstraints c = new RouteConstraints(
                LocalDateTime.of(2026, 9, 21, 6, 0), null, null, null, null, null, null,
                SEOUL_STATION, null, null, null);

        assertThat(c.effectiveDayStart(true)).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void 첫날_늦은_도착이면_도착_시각부터_시작한다() {
        RouteConstraints c = new RouteConstraints(
                LocalDateTime.of(2026, 9, 21, 18, 30), null, null, null, null, null, null,
                SEOUL_STATION, null, null, null);

        assertThat(c.effectiveDayStart(true)).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    void 첫날이어도_도착시각이_없으면_dayStartTime_그대로다() {
        RouteConstraints c = constraints(SEOUL_STATION, null);
        assertThat(c.effectiveDayStart(true)).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void 마지막날이_아니면_lastDayDepartureTime과_무관하게_dayEndTime_그대로다() {
        RouteConstraints c = new RouteConstraints(
                null, LocalTime.of(15, 0), null, null, null, null, null,
                SEOUL_STATION, null, null, null);

        assertThat(c.effectiveDayEnd(false)).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void 마지막날은_dayEndTime과_귀가시각_중_더_이른_쪽이다() {
        RouteConstraints c = new RouteConstraints(
                null, LocalTime.of(15, 0), null, null, null, null, null,
                SEOUL_STATION, null, null, null);

        assertThat(c.effectiveDayEnd(true)).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void 마지막날_귀가시각이_dayEndTime보다_늦으면_dayEndTime이_기준이다() {
        RouteConstraints c = new RouteConstraints(
                null, LocalTime.of(23, 0), null, null, null, null, null,
                SEOUL_STATION, null, null, null);

        assertThat(c.effectiveDayEnd(true)).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void 마지막날이어도_귀가시각이_없으면_dayEndTime_그대로다() {
        RouteConstraints c = constraints(SEOUL_STATION, null);
        assertThat(c.effectiveDayEnd(true)).isEqualTo(LocalTime.of(21, 0));
    }
}
