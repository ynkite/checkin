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

    // ── 결정 9-2 : dwellMinutes 비면 카테고리 기본값 ─────────────────────

    @Test
    void dwellMinutes를_주면_그대로_쓴다() {
        Candidate c = new Candidate("c1", "카페", LOCATION, CandidateCategory.CAFE, 25, null, null, null);
        assertThat(c.dwellMinutes()).isEqualTo(25);
    }

    @Test
    void dwellMinutes가_없으면_FOOD는_60분() {
        Candidate c = new Candidate("c1", "식당", LOCATION, CandidateCategory.FOOD, null, null, null, null);
        assertThat(c.dwellMinutes()).isEqualTo(60);
    }

    @Test
    void dwellMinutes가_없으면_TOUR는_90분() {
        Candidate c = new Candidate("c1", "관광지", LOCATION, CandidateCategory.TOUR, null, null, null, null);
        assertThat(c.dwellMinutes()).isEqualTo(90);
    }

    @Test
    void dwellMinutes가_없으면_CAFE는_40분() {
        Candidate c = new Candidate("c1", "카페", LOCATION, CandidateCategory.CAFE, null, null, null, null);
        assertThat(c.dwellMinutes()).isEqualTo(40);
    }

    @Test
    void 기본값은_접근자에서_계산될뿐_생성자에서_필드에_박히지_않는다() {
        Candidate explicit = new Candidate("c1", "식당", LOCATION, CandidateCategory.FOOD, 60, null, null, null);
        Candidate defaulted = new Candidate("c1", "식당", LOCATION, CandidateCategory.FOOD, null, null, null, null);

        // 접근자 결과는 우연히 같다(둘 다 60분) — 하지만 저장된 값 자체는 달라야 한다.
        assertThat(explicit.dwellMinutes()).isEqualTo(defaulted.dwellMinutes());
        assertThat(explicit).isNotEqualTo(defaulted);
    }
}
