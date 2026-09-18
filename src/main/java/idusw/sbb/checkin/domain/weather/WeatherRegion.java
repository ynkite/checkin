package idusw.sbb.checkin.domain.weather;

import idusw.sbb.checkin.domain.crowd.AreaCode;

import java.util.Map;

/**
 * 여행지 이름 -> 기상청 예보를 받을 수 있는 지역 이름.
 *
 * <p>왜 필요한가 — {@code WeatherServiceImpl} 의 격자표는 열 곳만 들고 있고,
 * 모르는 이름이 오면 <b>서울 격자로 조용히 떨어진다</b>. 동선에 날씨를 붙이는
 * 자리에서 그게 일어나면 「여수 여행인데 서울 날씨」가 실제 예보처럼 저장된다.
 * 그래서 여기서는 <b>아는 곳만 이름을 돌려주고 모르면 null 을 준다.</b>
 * 부르는 쪽은 null 이면 날씨 칸을 비워 둔다. 채우지 않는다.
 *
 * <p>「부산 해운대구」처럼 시군구까지 온 이름은 {@link AreaCode} 가 시도로
 * 줄여 준다. 그 시도를 예보 지역 이름으로 바꾸는 것이 이 표다.
 *
 * <p>아직 못 받는 곳 — 울산·세종·충북·충남·전남·경북·경남. 격자표에 없다.
 * 격자를 추가하면 이 표에도 같이 넣어야 한다.
 */
public final class WeatherRegion {

    /** 시도 이름 -> 예보 지역 이름. 값은 WeatherServiceImpl 의 격자표 열쇠와 같아야 한다. */
    private static final Map<String, String> BY_SIDO = Map.ofEntries(
            Map.entry("서울", "서울"),
            Map.entry("부산", "부산"),
            Map.entry("대구", "대구"),
            Map.entry("인천", "인천"),
            Map.entry("광주", "광주"),
            Map.entry("대전", "대전"),
            Map.entry("제주", "제주"),
            Map.entry("경기", "수원"),
            Map.entry("전북", "전주"),
            Map.entry("강원", "강릉")
    );

    private WeatherRegion() {}

    /**
     * @param destination 「부산」·「부산 해운대구」·「제주도」처럼 사람이 쓴 여행지 이름
     * @return 예보를 받을 수 있는 지역 이름. 모르는 곳이면 null
     */
    public static String of(String destination) {
        if (destination == null || destination.isBlank()) return null;

        String trimmed = destination.trim();

        /* 시군구까지 온 이름은 AreaCode 가 시도로 줄여 준다. 먼저 이걸 쓴다 —
           「경기 광주시」를 글자만 보고 고르면 전라도 광주로 잡힌다. */
        AreaCode.Area area = AreaCode.find(trimmed);
        if (area != null) {
            String byArea = BY_SIDO.get(area.sido());
            if (byArea != null) return byArea;
        }

        /* AreaCode 가 모르는 이름인데 예보 지역 이름이 그대로 섞여 있는 경우 */
        for (String region : BY_SIDO.values()) {
            if (trimmed.contains(region)) return region;
        }
        return null;
    }
}
