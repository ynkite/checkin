package idusw.sbb.checkin.domain.route.adapter;

import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code PlanInputForm.departure} 문자열 → 시·도 대표 좌표 1점 ({@code ..\09_시도_대표좌표.md}).
 * 결정 2의 우회비용 식에서 <b>귀가거점</b>이 이 좌표다.
 *
 * <p>이 좌표가 없으면 {@code d(숙소, 귀가거점)} 이 0이 되고 {@code maxDetour} 가 하한 5로
 * 주저앉아 귀가 밴드가 죽는다 (결정 10-(8)). 그래서 매칭 실패도 빈 값이 아니라 서울로 떨어뜨린다 —
 * 이 서비스의 출발지 분포상 서울이 가장 흔한 값이라, 빈 좌표보다 틀릴 확률이 낮다.
 *
 * <p>정밀도는 시·도청 수준이면 충분하다. 수백 km 짜리 벡터의 <b>방향</b>을 정하는 용도다.
 */
public final class DeparturePointResolver {

    private static final Logger log = LoggerFactory.getLogger(DeparturePointResolver.class);

    /** 매칭 실패 폴백 (서울시청). */
    public static final GeoPoint SEOUL = new GeoPoint(37.5665, 126.9780);

    private static final Map<String, GeoPoint> BY_ALIAS = new LinkedHashMap<>();
    private static final List<String> ALIASES_LONGEST_FIRST;

    static {
        register(SEOUL, "서울특별시", "서울");
        register(new GeoPoint(35.1796, 129.0756), "부산광역시", "부산");
        register(new GeoPoint(35.8714, 128.6014), "대구광역시", "대구");
        register(new GeoPoint(37.4563, 126.7052), "인천광역시", "인천");
        register(new GeoPoint(35.1595, 126.8526), "광주광역시", "광주");
        register(new GeoPoint(36.3504, 127.3845), "대전광역시", "대전");
        register(new GeoPoint(35.5384, 129.3114), "울산광역시", "울산");
        register(new GeoPoint(36.4800, 127.2890), "세종특별자치시", "세종");
        register(new GeoPoint(37.2636, 127.0286), "경기도", "경기");
        register(new GeoPoint(37.8813, 127.7298), "강원특별자치도", "강원도", "강원");
        register(new GeoPoint(36.6424, 127.4890), "충청북도", "충북");
        register(new GeoPoint(36.6588, 126.6728), "충청남도", "충남");
        register(new GeoPoint(35.8242, 127.1480), "전북특별자치도", "전라북도", "전북");
        register(new GeoPoint(34.8161, 126.4629), "전라남도", "전남");
        register(new GeoPoint(36.5684, 128.7294), "경상북도", "경북");
        register(new GeoPoint(35.2280, 128.6811), "경상남도", "경남");
        register(new GeoPoint(33.4890, 126.4983), "제주특별자치도", "제주도", "제주");

        // 앞머리 자르기는 긴 별칭부터 봐야 한다 — "제주특별자치도 서귀포시" 가 "제주" 에 먼저 걸리면
        // 결과는 같아도 "강원특별자치도" 류에서 잘못된 짧은 별칭에 걸릴 여지가 생긴다.
        ALIASES_LONGEST_FIRST = new ArrayList<>(BY_ALIAS.keySet());
        ALIASES_LONGEST_FIRST.sort(Comparator.comparingInt(String::length).reversed());
    }

    private DeparturePointResolver() {
    }

    private static void register(GeoPoint point, String... aliases) {
        for (String alias : aliases) {
            BY_ALIAS.put(alias, point);
        }
    }

    /**
     * @param departure {@code "서울"} · {@code "서울특별시"} · {@code "경기도 성남시"} 같은 출발지 문자열
     * @return 그 시·도의 대표 좌표. 못 찾으면 {@link #SEOUL} (WARN 로그를 남긴다)
     */
    public static GeoPoint resolve(String departure) {
        if (departure != null) {
            String normalized = departure.trim();
            GeoPoint exact = BY_ALIAS.get(normalized);
            if (exact != null) {
                return exact;
            }
            for (String alias : ALIASES_LONGEST_FIRST) {
                if (normalized.startsWith(alias)) {
                    return BY_ALIAS.get(alias);
                }
            }
        }
        log.warn("출발지에서 시·도를 못 찾아 서울로 폴백한다 — departure={}", departure);
        return SEOUL;
    }
}
