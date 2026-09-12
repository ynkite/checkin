package idusw.sbb.checkin.domain.route.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

/**
 * 부산·경주·강릉 각 25개 이상을 하드코딩한 개발용 {@link PlaceCandidateProvider}.
 * 이수환의 관광공사 응답 구조가 확정되기 전까지 BandSplitter 이후 단계를 실제 지역
 * 데이터로 검증하기 위한 것이다.
 *
 * <p>좌표는 전부 실제 랜드마크다. 앵커(해운대·보문단지·경포호 인근 숙소 가정)를 기준으로
 * 근거리(&lt;5km)·중거리(5~25km)·귀가 방향(25km~)이 실제로 갈리도록 의도적으로 세
 * 거리대 전부에 후보를 흩어 놨다 — 후보가 한쪽에 몰리면 BandSplitter 를 검증할 수
 * 없기 때문이다(정확한 거리대 배정은 좌표·기본 경계값 기준으로 좌표 선정 시 직접
 * 확인했다). 카테고리(FOOD/TOUR/CAFE)와 영업시간(상시 개방 vs 정해진 시간)도 섞었다.
 *
 * <p>{@code startDate}/{@code endDate} 는 쓰지 않는다 — 스텁 데이터라 계절·요일과
 * 무관하다.
 */
public final class StubCandidateProvider implements PlaceCandidateProvider {

    @Override
    public List<Candidate> findCandidates(String region, LocalDate startDate, LocalDate endDate) {
        if (region == null) {
            throw new IllegalArgumentException("region must not be null");
        }
        return switch (region.trim()) {
            case "부산" -> BUSAN;
            case "경주" -> GYEONGJU;
            case "강릉" -> GANGNEUNG;
            default -> throw new IllegalArgumentException(
                    "StubCandidateProvider 가 지원하지 않는 지역: " + region + " (부산/경주/강릉만 지원)");
        };
    }

    private static Candidate place(String id, String name, double lat, double lng, CandidateCategory category,
                                    LocalTime openTime, LocalTime closeTime) {
        return new Candidate(id, name, new GeoPoint(lat, lng), category, null, openTime, closeTime, null);
    }

    private static Candidate place(String id, String name, double lat, double lng, CandidateCategory category,
                                    LocalTime openTime, LocalTime closeTime, DayOfWeek closedDay) {
        return new Candidate(id, name, new GeoPoint(lat, lng), category, null, openTime, closeTime,
                EnumSet.of(closedDay));
    }

    // ── 부산 (앵커 가정: 해운대해수욕장 인근 숙소, 35.1587, 129.1604) ──────────

    private static final List<Candidate> BUSAN = List.of(
            place("busan-haeundae-market", "해운대시장", 35.1633, 129.1603, CandidateCategory.FOOD,
                    LocalTime.of(9, 0), LocalTime.of(21, 0)),
            place("busan-dongbaek-island", "동백섬", 35.1553, 129.1499, CandidateCategory.TOUR, null, null),
            place("busan-nurimaru", "누리마루APEC하우스", 35.1553, 129.1510, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("busan-aquarium", "부산아쿠아리움", 35.1587, 129.1544, CandidateCategory.TOUR,
                    LocalTime.of(10, 0), LocalTime.of(19, 0)),
            place("busan-cheongsapo", "청사포", 35.1633, 129.1892, CandidateCategory.TOUR, null, null),
            place("busan-dalmaji-road", "달맞이길카페거리", 35.1567, 129.1789, CandidateCategory.CAFE,
                    LocalTime.of(8, 0), LocalTime.of(22, 0)),
            place("busan-mipo-station", "미포정거장", 35.1633, 129.1830, CandidateCategory.TOUR, null, null),
            place("busan-centum-cafe", "센텀시티카페거리", 35.1691, 129.1310, CandidateCategory.CAFE,
                    LocalTime.of(8, 0), LocalTime.of(22, 0)),
            place("busan-gwangalli-beach", "광안리해수욕장", 35.1532, 129.1187, CandidateCategory.TOUR, null, null),
            place("busan-tower", "부산타워", 35.1015, 129.0324, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(22, 0)),
            place("busan-jagalchi-market", "자갈치시장", 35.0965, 129.0306, CandidateCategory.FOOD,
                    LocalTime.of(7, 0), LocalTime.of(21, 0)),
            place("busan-gamcheon-village", "감천문화마을", 35.0973, 129.0106, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("busan-taejongdae", "태종대", 35.0511, 129.0864, CandidateCategory.TOUR, null, null),
            place("busan-gukje-market", "국제시장", 35.1002, 129.0298, CandidateCategory.FOOD,
                    LocalTime.of(9, 0), LocalTime.of(21, 0)),
            place("busan-songjeong-beach", "송정해수욕장", 35.1786, 129.2007, CandidateCategory.TOUR, null, null),
            place("busan-oryukdo-skywalk", "오륙도스카이워크", 35.1015, 129.1128, CandidateCategory.TOUR, null, null),
            place("busan-station", "부산역", 35.1152, 129.0415, CandidateCategory.TOUR, null, null),
            place("busan-beomeosa", "범어사", 35.2185, 129.0623, CandidateCategory.TOUR,
                    LocalTime.of(8, 0), LocalTime.of(18, 0)),
            place("busan-igidae-park", "이기대공원", 35.1276, 129.1233, CandidateCategory.TOUR, null, null),
            place("busan-huinnyeoul-village", "흰여울문화마을", 35.0759, 129.0402, CandidateCategory.TOUR, null, null),
            place("busan-gimhae-viewpoint", "김해공항전망대", 35.1795, 128.9382, CandidateCategory.TOUR, null, null),
            place("busan-jinhae-gyeonghwa-station", "진해경화역", 35.1467, 128.6942, CandidateCategory.TOUR, null, null),
            place("busan-changwon-yongji", "창원용지호수공원", 35.2281, 128.6811, CandidateCategory.TOUR, null, null),
            place("busan-miryang-valley", "밀양얼음골", 35.5843, 128.9646, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(17, 0)),
            place("busan-tongdosa", "통도사", 35.4924, 129.0655, CandidateCategory.TOUR,
                    LocalTime.of(8, 0), LocalTime.of(18, 0)),
            place("busan-geoje-beach", "학동몽돌해변", 34.7550, 128.5980, CandidateCategory.TOUR, null, null),
            place("busan-tongyeong-dongpirang", "동피랑마을", 34.8440, 128.4335, CandidateCategory.TOUR, null, null)
    );

    // ── 경주 (앵커 가정: 보문관광단지 인근 숙소, 35.8380, 129.2748) ────────────

    private static final List<Candidate> GYEONGJU = List.of(
            place("gyeongju-world", "경주월드", 35.8320, 129.2570, CandidateCategory.TOUR,
                    LocalTime.of(10, 0), LocalTime.of(21, 0)),
            place("gyeongju-bomun-lake", "보문호수", 35.8395, 129.2790, CandidateCategory.TOUR, null, null),
            place("gyeongju-hwangnidan-gil", "황리단길", 35.8355, 129.2115, CandidateCategory.CAFE,
                    LocalTime.of(8, 0), LocalTime.of(22, 0)),
            place("gyeongju-expo-park", "경주엑스포공원", 35.8420, 129.2830, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("gyeongju-bomun-cafe", "보문단지카페거리", 35.8400, 129.2760, CandidateCategory.CAFE,
                    LocalTime.of(8, 0), LocalTime.of(22, 0)),
            place("gyeongju-cheomseongdae", "첨성대", 35.8353, 129.2194, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(22, 0)),
            place("gyeongju-daereungwon", "대릉원", 35.8367, 129.2124, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(22, 0)),
            place("gyeongju-anapji", "동궁과월지", 35.8347, 129.2247, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(22, 0)),
            place("gyeongju-station", "경주역", 35.8423, 129.2093, CandidateCategory.TOUR, null, null),
            place("gyeongju-museum", "국립경주박물관", 35.8305, 129.2262, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0), DayOfWeek.MONDAY),
            place("gyeongju-gyerim", "계림", 35.8347, 129.2192, CandidateCategory.TOUR, null, null),
            place("gyeongju-bulguksa", "불국사", 35.7898, 129.3320, CandidateCategory.TOUR,
                    LocalTime.of(8, 0), LocalTime.of(18, 0)),
            place("gyeongju-seokguram", "석굴암", 35.7947, 129.3493, CandidateCategory.TOUR,
                    LocalTime.of(8, 0), LocalTime.of(17, 0)),
            place("gyeongju-yangdong-village", "양동마을", 35.7867, 129.2081, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("gyeongju-wolseong", "월성", 35.8293, 129.2225, CandidateCategory.TOUR, null, null),
            place("gyeongju-gameunsaji", "감은사지", 35.7347, 129.4517, CandidateCategory.TOUR, null, null),
            place("gyeongju-munmudae", "문무대왕릉", 35.7089, 129.5250, CandidateCategory.TOUR, null, null),
            place("gyeongju-jusangjeolli", "주상절리파도소리길", 35.6928, 129.4650, CandidateCategory.TOUR, null, null),
            place("gyeongju-pohang-homigot", "호미곶", 36.0761, 129.5686, CandidateCategory.TOUR, null, null),
            place("gyeongju-ulsan-taehwagang", "태화강국가정원", 35.5384, 129.3114, CandidateCategory.TOUR, null, null),
            place("gyeongju-yeongcheon-eunhaesa", "영천은해사", 35.9733, 128.9386, CandidateCategory.TOUR,
                    LocalTime.of(8, 0), LocalTime.of(18, 0)),
            place("gyeongju-cheongdo-provence", "청도프로방스", 35.6474, 128.7345, CandidateCategory.TOUR, null, null),
            place("gyeongju-daegu-dongseongro", "대구동성로", 35.8695, 128.5946, CandidateCategory.FOOD,
                    LocalTime.of(11, 0), LocalTime.of(22, 0)),
            place("gyeongju-eonyang-bulgogi", "언양불고기거리", 35.5652, 129.1136, CandidateCategory.FOOD,
                    LocalTime.of(11, 0), LocalTime.of(21, 0)),
            place("gyeongju-pohang-mulhoe", "포항물회거리", 36.0190, 129.3435, CandidateCategory.FOOD,
                    LocalTime.of(11, 0), LocalTime.of(21, 0)),
            place("gyeongju-iu-valley", "이유당계곡", 35.9560, 129.1620, CandidateCategory.TOUR, null, null)
    );

    // ── 강릉 (앵커 가정: 경포호 인근 숙소, 37.8058, 128.8968) ─────────────────

    private static final List<Candidate> GANGNEUNG = List.of(
            place("gangneung-gyeongpodae", "경포대", 37.7958, 128.8965, CandidateCategory.TOUR, null, null),
            place("gangneung-gyeongpo-beach", "경포해변", 37.8054, 128.9095, CandidateCategory.TOUR, null, null),
            place("gangneung-gangmun-beach", "강문해변", 37.7936, 128.9153, CandidateCategory.TOUR, null, null),
            place("gangneung-ojukheon", "오죽헌", 37.7847, 128.8887, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("gangneung-chodang-village", "초당순두부마을", 37.7897, 128.9159, CandidateCategory.FOOD,
                    LocalTime.of(8, 0), LocalTime.of(20, 0)),
            place("gangneung-anmok-coffee", "안목커피거리", 37.7723, 128.9425, CandidateCategory.CAFE,
                    LocalTime.of(8, 0), LocalTime.of(22, 0)),
            place("gangneung-station", "강릉역", 37.7639, 128.8996, CandidateCategory.TOUR, null, null),
            place("gangneung-market", "강릉중앙시장", 37.7519, 128.8963, CandidateCategory.FOOD,
                    LocalTime.of(9, 0), LocalTime.of(21, 0)),
            place("gangneung-jumunjin-beach", "주문진해변", 37.8971, 128.8237, CandidateCategory.TOUR, null, null),
            place("gangneung-gyeongpo-lake-cafe", "경포호수카페거리", 37.8010, 128.9200, CandidateCategory.CAFE,
                    LocalTime.of(8, 0), LocalTime.of(22, 0)),
            place("gangneung-jeongdongjin", "정동진", 37.6903, 129.0347, CandidateCategory.TOUR, null, null),
            place("gangneung-daegwallyeong-ranch", "대관령양떼목장", 37.6844, 128.7358, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(17, 0)),
            place("gangneung-woljeongsa", "월정사", 37.7317, 128.5967, CandidateCategory.TOUR,
                    LocalTime.of(8, 0), LocalTime.of(18, 0)),
            place("gangneung-hoenggye-hwangtae", "횡계황태마을", 37.6403, 128.7159, CandidateCategory.FOOD,
                    LocalTime.of(10, 0), LocalTime.of(20, 0)),
            place("gangneung-jinbu-market", "진부시장", 37.7048, 128.5237, CandidateCategory.TOUR, null, null),
            place("gangneung-pyeongchang-town", "평창읍", 37.3705, 128.3903, CandidateCategory.TOUR, null, null),
            place("gangneung-wonju-hanji", "원주한지테마파크", 37.3422, 127.9202, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("gangneung-hongcheon-river", "홍천강", 37.6971, 127.8887, CandidateCategory.TOUR, null, null),
            place("gangneung-sokcho-beach", "속초해변", 38.2070, 128.5918, CandidateCategory.TOUR, null, null),
            place("gangneung-donghae-chuam", "동해추암촛대바위", 37.5075, 129.1319, CandidateCategory.TOUR, null, null),
            place("gangneung-samcheok-beach", "삼척해변", 37.4500, 129.1750, CandidateCategory.TOUR, null, null),
            place("gangneung-daedohobu-gwana", "강릉대도호부관아", 37.7540, 128.8955, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("gangneung-haslla-art-world", "하슬라아트월드", 37.6390, 129.1930, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(18, 0)),
            place("gangneung-anbandegi", "안반데기", 37.6122, 128.6763, CandidateCategory.TOUR, null, null),
            place("gangneung-jeongdong-simgok", "정동심곡바다부채길", 37.6853, 129.0389, CandidateCategory.TOUR,
                    LocalTime.of(9, 0), LocalTime.of(17, 0)),
            place("gangneung-sogeumgang", "소금강", 37.7742, 128.6349, CandidateCategory.TOUR, null, null)
    );
}
