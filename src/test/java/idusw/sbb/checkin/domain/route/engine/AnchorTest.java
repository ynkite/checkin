package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnchorTest {

    private static final GeoPoint LOCATION = new GeoPoint(35.1595, 129.0756);

    @Test
    void id가_비어있으면_예외() {
        assertThatThrownBy(() -> new Anchor("", "해운대 숙소", LOCATION, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이름이_비어있으면_예외() {
        assertThatThrownBy(() -> new Anchor("a1", " ", LOCATION, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 좌표가_없으면_예외() {
        assertThatThrownBy(() -> new Anchor("a1", "해운대 숙소", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 다른_지점까지의_거리를_위임한다() {
        Anchor anchor = new Anchor("a1", "해운대 숙소", LOCATION, null, null);
        GeoPoint other = new GeoPoint(35.1796, 129.0756);

        assertThat(anchor.distanceKmTo(other)).isEqualTo(LOCATION.distanceKmTo(other));
    }

    @Test
    void 체크인_시각_이전이면_불가() {
        Anchor anchor = new Anchor("a1", "해운대 숙소", LOCATION, LocalTime.of(15, 0), null);

        assertThat(anchor.isCheckInAvailableAt(LocalTime.of(14, 59))).isFalse();
        assertThat(anchor.isCheckInAvailableAt(LocalTime.of(15, 0))).isTrue();
        assertThat(anchor.isCheckInAvailableAt(LocalTime.of(18, 0))).isTrue();
    }

    @Test
    void 체크인_시각이_없으면_항상_가능() {
        Anchor anchor = new Anchor("a1", "해운대 숙소", LOCATION, null, null);
        assertThat(anchor.isCheckInAvailableAt(LocalTime.of(0, 0))).isTrue();
    }
}
