package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TourApiCandidateProviderTest {

    @Test
    void 아직_미구현이라_호출하면_예외를_던진다() {
        PlaceCandidateProvider provider = new TourApiCandidateProvider();
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 3);

        assertThatThrownBy(() -> provider.findCandidates("부산", start, end))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
