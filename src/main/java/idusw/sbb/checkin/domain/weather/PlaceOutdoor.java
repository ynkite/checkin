package idusw.sbb.checkin.domain.weather;

import java.util.List;

/**
 * 이 장소는 비를 맞는 곳인가.
 *
 * <p>비 예보가 걸린 날에 <b>야외 장소만</b> 실내 대안을 권하기 위해 쓴다.
 * 하루를 통째로 갈아 끼우지 않는다 — 박물관은 비가 와도 그대로 가면 된다.
 *
 * <p>이름을 먼저 본다. 「부산현대미술관」은 분류가 관광지지만 실내고,
 * 「해운대해수욕장」은 이름만 봐도 야외다. 이름에 단서가 없을 때만 분류로 간다 —
 * 관광지는 야외로, 먹고 자고 마시는 곳은 실내로 본다.
 *
 * <p>완벽한 분류가 아니다. 틀린 장소가 보이면 아래 낱말 목록에 더하면 된다.
 * 틀리는 방향은 한쪽으로 몰려 있다 — 실내를 야외로 잘못 보면 필요 없는 대안을
 * 권하는 정도지만, 야외를 실내로 잘못 보면 비 오는 날 그대로 내보낸다.
 * 그래서 애매하면 야외로 둔다.
 */
public final class PlaceOutdoor {

    private static final List<String> OUTDOOR_WORDS = List.of(
            "해수욕장", "해변", "해안", "바닷가", "공원", "숲길", "둘레길", "산책로",
            "등산", "계곡", "폭포", "저수지", "호수", "정원", "수목원", "식물원",
            "전망대", "광장", "야외", "캠핑", "낚시", "포구", "성곽",
            "올레", "갯벌", "오름", "테마파크", "동물원", "시장");

    private static final List<String> INDOOR_WORDS = List.of(
            "박물관", "미술관", "아쿠아리움", "수족관", "전시관", "전시장", "갤러리",
            "도서관", "실내", "백화점", "아웃렛", "쇼핑몰", "과학관", "기념관",
            "체험관", "영화관", "극장", "공연장", "스파", "온천", "찜질방", "사우나",
            "문화센터", "플라자", "기차역", "터미널", "공항", "면세점", "쇼핑");

    /* 카카오 category_name(「교통,수송 > 기차,철도 > 기차역」)이나 관광공사 분류에
       이 말이 들어 있으면 실내다. 이름에 단서가 없는 「부산역」 「센텀시티」를 여기서 가른다 */
    private static final List<String> INDOOR_CATEGORY = List.of(
            "기차역", "지하철역", "터미널", "공항", "백화점", "쇼핑몰", "대형마트", "면세점",
            "아울렛", "아웃렛", "박물관", "미술관", "전시관", "영화관", "공연장", "아쿠아리움",
            "실내", "문화시설", "쇼핑");

    private PlaceOutdoor() {}

    /**
     * @param name 장소 이름
     * @param type 동선 JSON 의 분류 — tour | food | cafe | stay
     */
    public static boolean is(String name, String type) {
        return is(name, type, null);
    }

    /** @param category 카카오 category_name 이나 관광공사 분류. 없으면 null */
    public static boolean is(String name, String type, String category) {
        String n = name == null ? "" : name.trim();
        String c = category == null ? "" : category;

        for (String w : OUTDOOR_WORDS) if (n.contains(w)) return true;
        for (String w : INDOOR_WORDS)  if (n.contains(w)) return false;
        for (String w : INDOOR_CATEGORY) if (c.contains(w)) return false;
        /* 「부산역」 「송정역」처럼 역으로 끝나면 역사 안이다 */
        if (n.endsWith("역")) return false;

        return "tour".equals(type);
    }
}
