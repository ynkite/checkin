package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateTest {

    private static final GeoPoint LOCATION = new GeoPoint(35.1595, 129.0756);

    private Candidate candidate(LocalTime open, LocalTime close, Set<DayOfWeek> closedDays) {
        return new Candidate("c1", "감천문화마을", LOCATION, CandidateCategory.TOUR, 90, open, close, closedDays);
    }

    @Test
    void 체류시간이_0이하이면_예외() {
        assertThatThrownBy(() ->
                new Candidate("c1", "감천문화마을", LOCATION, CandidateCategory.TOUR, 0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 영업시작만_있고_영업종료가_없으면_예외() {
        assertThatThrownBy(() -> candidate(LocalTime.of(9, 0), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 영업시간이_없으면_항상_영업중() {
        Candidate c = candidate(null, null, null);
        assertThat(c.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(3, 0))).isTrue();
    }

    @Test
    void 영업시간_밖이면_영업중이_아니다() {
        Candidate c = candidate(LocalTime.of(9, 0), LocalTime.of(18, 0), null);

        assertThat(c.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(8, 59))).isFalse();
        assertThat(c.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(9, 0))).isTrue();
        assertThat(c.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(18, 0))).isTrue();
        assertThat(c.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(18, 1))).isFalse();
    }

    @Test
    void 휴무일이면_영업시간_안이어도_닫혀있다() {
        Candidate c = candidate(LocalTime.of(9, 0), LocalTime.of(18, 0), Set.of(DayOfWeek.MONDAY));

        assertThat(c.isOpenAt(DayOfWeek.MONDAY, LocalTime.of(12, 0))).isFalse();
        assertThat(c.isOpenAt(DayOfWeek.TUESDAY, LocalTime.of(12, 0))).isTrue();
    }

    @Test
    void 빈_휴무일_집합도_정상_처리된다() {
        Candidate c = candidate(null, null, Set.of());
        assertThat(c.closedDays()).isEmpty();
    }
}
