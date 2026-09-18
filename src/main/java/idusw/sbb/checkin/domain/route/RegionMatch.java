package idusw.sbb.checkin.domain.route;

/**
 * 카카오가 준 주소가 우리가 고른 지역의 것인가.
 *
 * <p>왜 필요한가 — 후보 수집이 「주소에 시군구 이름이 그대로 들어 있는가」로 걸렀다.
 * 그런데 행정구역 이름은 부르는 말과 주소에 적히는 말이 다르다.
 *
 * <pre>
 *   고른 지역   「세종 세종시」 -> 시군구 「세종시」
 *   카카오 주소  「세종특별자치시 연기면 보통리 183-2」
 *   "세종특별자치시".contains("세종시")  ==  false
 * </pre>
 *
 * <p>그래서 <b>모든 후보가 탈락했다.</b> 숙소도 맛집도 관광지도 0개가 되고,
 * 일정이 통째로 비어 3단계에서 4단계로 넘어가지 못했다. 실제로 일어난 일이고
 * 세종으로 여행을 짜려던 사람은 아무것도 할 수 없었다.
 *
 * <p>세종만이 아니다. 강원특별자치도·전북특별자치도·제주특별자치도도 같은 모양이다.
 *
 * <p>고치는 방법은 꼬리말을 떼고 뼈대만 비교하는 것이다 —
 * 「세종시」와 「세종특별자치시」는 둘 다 뼈대가 「세종」이다.
 * 느슨해지는 만큼 다른 지역이 섞일 수 있지만, 질의 자체에 여행지 이름이 들어가 있고
 * 수집 뒤에 중앙값에서 80km 넘는 것을 걷어내는 단계가 따로 있다.
 * <b>엉뚱한 곳이 하나 섞이는 편이 아무것도 못 찾는 것보다 낫다.</b>
 */
public final class RegionMatch {

    /** 긴 것부터 뗀다. 「특별자치시」를 「시」보다 먼저 봐야 「세종특별자치」가 남지 않는다. */
    private static final String[] SUFFIXES = {
            "특별자치시", "특별자치도", "광역시", "특별시", "자치시", "자치도",
            "시", "군", "구", "도"
    };

    private RegionMatch() {}

    /**
     * 행정구역 꼬리말을 뗀 뼈대. 「세종시」·「세종특별자치시」 -> 「세종」, 「해운대구」 -> 「해운대」.
     * 뗐더니 아무것도 안 남으면(「시」처럼) 원래 값을 그대로 돌려준다.
     */
    public static String core(String region) {
        if (region == null) return "";
        String s = region.trim();
        if (s.isEmpty()) return "";

        for (String suffix : SUFFIXES) {
            if (s.length() > suffix.length() && s.endsWith(suffix)) {
                return s.substring(0, s.length() - suffix.length());
            }
        }
        return s;
    }

    /**
     * 이 주소가 그 지역의 것인가.
     *
     * @param region 고른 시군구. 비어 있으면 거르지 않는다(true)
     * @param addr   카카오 지번 주소
     * @param road   카카오 도로명 주소
     */
    public static boolean matches(String region, String addr, String road) {
        if (region == null || region.isBlank()) return true;

        String a = addr == null ? "" : addr;
        String r = road == null ? "" : road;
        if (a.contains(region) || r.contains(region)) return true;

        String core = core(region);
        if (core.isBlank()) return false;
        return a.contains(core) || r.contains(core);
    }
}
