package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TourApiCandidateProviderTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 3);

    /* 행 모양은 2026-09 경주시 KorService2 areaBasedList2 실제 응답에서 옮겼다 */
    private static final List<TourApiCandidateProvider.Row> TOURS = List.of(
            new TourApiCandidateProvider.Row("126207", "불국사", "129.3318", "35.7899", "A02010800"),
            new TourApiCandidateProvider.Row("128677", "감포항", "129.504", "35.8078", "A01011400"),
            new TourApiCandidateProvider.Row("", "id 없음", "129.1", "35.8", "A02010800"),
            new TourApiCandidateProvider.Row("9", "좌표 없음", "", "", "A02010800"),
            new TourApiCandidateProvider.Row("8", "영점", "0", "0", "A02010800"));
    private static final List<TourApiCandidateProvider.Row> FOODS = List.of(
            new TourApiCandidateProvider.Row("111", "갈치조림 토박이식당", "129.21", "35.8523", "A05020100"),
            new TourApiCandidateProvider.Row("222", "경주 블리스커피", "129.20", "35.7983", "A05020900"),
            new TourApiCandidateProvider.Row("126207", "불국사", "129.3318", "35.7899", "A05020100"));

    @Test
    void 관광지는_TOUR_음식점은_FOOD_카페_코드는_CAFE_로_나누고_못_쓰는_행은_뺀다() {
        PlaceCandidateProvider p = new TourApiCandidateProvider((region, type) -> "12".equals(type) ? TOURS : FOODS);
        List<Candidate> cs = p.findCandidates("경주", START, END);

        assertThat(cs).extracting(Candidate::name)
                .containsExactly("불국사", "감포항", "갈치조림 토박이식당", "경주 블리스커피");
        assertThat(cs).extracting(Candidate::category).containsExactly(
                CandidateCategory.TOUR, CandidateCategory.TOUR, CandidateCategory.FOOD, CandidateCategory.CAFE);
        assertThat(cs.get(0).id()).isEqualTo("tour:126207");
        assertThat(cs.get(0).location().latitude()).isEqualTo(35.7899);
        assertThat(cs.get(0).openTime()).isNull();   // 모르는 영업시간을 지어내지 않는다
    }

    @Test
    void 호출이_실패하면_빈_목록이_아니라_예외다() {
        PlaceCandidateProvider p = new TourApiCandidateProvider((region, type) -> "12".equals(type) ? TOURS : null);
        assertThatThrownBy(() -> p.findCandidates("경주", START, END))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 받았는데_0건이면_빈_목록이다() {
        PlaceCandidateProvider p = new TourApiCandidateProvider((region, type) -> List.of());
        assertThat(p.findCandidates("아틀란티스", START, END)).isEmpty();
    }
}
