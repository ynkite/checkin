package idusw.sbb.checkin.domain.route.adapter;

import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeparturePointResolverTest {

    @Test
    void 정식_명칭이_들어오면_그대로_매칭한다() {
        assertThat(DeparturePointResolver.resolve("서울특별시")).isEqualTo(new GeoPoint(37.5665, 126.9780));
        assertThat(DeparturePointResolver.resolve("경상남도")).isEqualTo(new GeoPoint(35.2280, 128.6811));
        assertThat(DeparturePointResolver.resolve("제주특별자치도")).isEqualTo(new GeoPoint(33.4890, 126.4983));
    }

    /** 프론트 출발지 select 가 주는 값이 약칭이다 (서울·경기·인천·강원·충남·대전·부산·제주). */
    @Test
    void 프론트가_주는_약칭_여덟개가_전부_매칭된다() {
        for (String alias : List.of("서울", "경기", "인천", "강원", "충남", "대전", "부산", "제주")) {
            assertThat(DeparturePointResolver.resolve(alias))
                    .as(alias)
                    .isNotEqualTo(null)
                    .satisfies(point -> assertThat(point.latitude()).isBetween(33.0, 38.5));
        }
        assertThat(DeparturePointResolver.resolve("경기")).isEqualTo(new GeoPoint(37.2636, 127.0286));
        assertThat(DeparturePointResolver.resolve("강원")).isEqualTo(new GeoPoint(37.8813, 127.7298));
    }

    /** 실제 저장값은 {@code depProv + " " + depCity} 라 시·군·구가 붙어 들어온다. */
    @Test
    void 시군구가_붙어도_앞머리_시도로_매칭한다() {
        assertThat(DeparturePointResolver.resolve("서울 강남구")).isEqualTo(DeparturePointResolver.SEOUL);
        assertThat(DeparturePointResolver.resolve("경기도 성남시")).isEqualTo(new GeoPoint(37.2636, 127.0286));
        assertThat(DeparturePointResolver.resolve("경상북도 경주시")).isEqualTo(new GeoPoint(36.5684, 128.7294));
        assertThat(DeparturePointResolver.resolve("부산광역시 해운대구")).isEqualTo(new GeoPoint(35.1796, 129.0756));
    }

    @Test
    void 옛_명칭도_같은_좌표로_붙는다() {
        assertThat(DeparturePointResolver.resolve("강원도")).isEqualTo(DeparturePointResolver.resolve("강원특별자치도"));
        assertThat(DeparturePointResolver.resolve("전라북도")).isEqualTo(DeparturePointResolver.resolve("전북특별자치도"));
        assertThat(DeparturePointResolver.resolve("제주도")).isEqualTo(DeparturePointResolver.resolve("제주특별자치도"));
    }

    @Test
    void 앞뒤_공백은_무시한다() {
        assertThat(DeparturePointResolver.resolve("  부산 ")).isEqualTo(new GeoPoint(35.1796, 129.0756));
    }

    // ── 매칭 실패 폴백 : 빈 좌표를 주면 maxDetour 가 하한으로 주저앉는다 (결정 10-8) ──

    @Test
    void 매칭에_실패하면_서울로_폴백한다() {
        assertThat(DeparturePointResolver.resolve(null)).isEqualTo(DeparturePointResolver.SEOUL);
        assertThat(DeparturePointResolver.resolve("")).isEqualTo(DeparturePointResolver.SEOUL);
        assertThat(DeparturePointResolver.resolve("   ")).isEqualTo(DeparturePointResolver.SEOUL);
        assertThat(DeparturePointResolver.resolve("도쿄")).isEqualTo(DeparturePointResolver.SEOUL);
        assertThat(DeparturePointResolver.resolve("어디로든")).isEqualTo(DeparturePointResolver.SEOUL);
    }

    @Test
    void 열일곱개_시도가_전부_서로_다른_좌표를_준다() {
        List<String> provinces = List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
                "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

        assertThat(provinces).map(DeparturePointResolver::resolve).doesNotHaveDuplicates();
    }
}
