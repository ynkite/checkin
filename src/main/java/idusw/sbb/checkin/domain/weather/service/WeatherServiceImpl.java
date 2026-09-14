package idusw.sbb.checkin.domain.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.weather.dto.DayWeather;
import idusw.sbb.checkin.domain.weather.dto.WeatherResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    @Value("${weather.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, int[]> REGION_GRID = Map.of(
            "서울",  new int[]{60, 127},
            "부산",  new int[]{98,  76},
            "제주",  new int[]{52,  38},
            "강릉",  new int[]{92, 131},
            "대전",  new int[]{67, 100},
            "대구",  new int[]{89,  90},
            "광주",  new int[]{58,  74},
            "인천",  new int[]{55, 124},
            "전주",  new int[]{63,  89},
            "수원",  new int[]{60, 121}
    );

    // 중기육상예보 지역코드 (단기예보 지역과 매핑)
    private static final Map<String, String> MID_LAND_CODE = Map.of(
            "서울",  "11B00000",
            "인천",  "11B00000",
            "수원",  "11B00000",
            "부산",  "11H20000",
            "대구",  "11H10000",
            "광주",  "11F20000",
            "전주",  "11F10000",
            "대전",  "11C20000",
            "강릉",  "11D20000",
            "제주",  "11G00000"
    );

    // 중기기온예보 지역코드
    private static final Map<String, String> MID_TA_CODE = Map.of(
            "서울",  "11B10101",
            "인천",  "11B20201",
            "수원",  "11B20601",
            "부산",  "11H20201",
            "대구",  "11H10701",
            "광주",  "11F20501",
            "전주",  "11F10201",
            "대전",  "11C20401",
            "강릉",  "11D20501",
            "제주",  "11G00201"
    );

    @Override
    public List<WeatherResponseDto> getForecast(String region) {
        // 단기예보 (오늘~3일차)
        List<WeatherResponseDto> shortTerm = getShortTermForecast(region);

        // 중기예보 (4~7일차)
        List<WeatherResponseDto> midTerm = getMidTermForecast(region, shortTerm.size());

        // 합치기
        List<WeatherResponseDto> result = new ArrayList<>(shortTerm);
        result.addAll(midTerm);
        return result;
    }


    // 단기예보 (getVilageFcst) - 오늘~3일치
    private List<WeatherResponseDto> getShortTermForecast(String region) {
        int[] grid = REGION_GRID.getOrDefault(region, REGION_GRID.get("서울"));

        LocalDateTime base = getBaseDateTime(LocalDateTime.now());
        String baseDate = base.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = base.format(DateTimeFormatter.ofPattern("HHmm"));

        String url = "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst"
                + "?pageNo=1&numOfRows=1000&dataType=JSON"
                + "&base_date=" + baseDate
                + "&base_time=" + baseTime
                + "&nx=" + grid[0]
                + "&ny=" + grid[1]
                + "&authKey=" + apiKey;

        try {
            String raw = restTemplate.getForObject(url, String.class);
            return parseShortTerm(raw);
        } catch (Exception e) {
            throw new RuntimeException("단기예보 호출 실패: " + e.getMessage(), e);
        }
    }

    private List<WeatherResponseDto> parseShortTerm(String raw) throws Exception {
        JsonNode items = objectMapper.readTree(raw)
                .path("response").path("body").path("items").path("item");

        Map<String, Map<String, String>> byDate = new LinkedHashMap<>();
        for (JsonNode item : items) {
            String time     = item.path("fcstTime").asText();
            if (!"1200".equals(time)) continue;
            String date     = item.path("fcstDate").asText();
            String category = item.path("category").asText();
            String value    = item.path("fcstValue").asText();
            byDate.computeIfAbsent(date, k -> new HashMap<>()).put(category, value);
        }

        List<WeatherResponseDto> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : byDate.entrySet()) {
            if (result.size() >= 3) break;  // 단기는 3일치만
            Map<String, String> v = entry.getValue();
            result.add(WeatherResponseDto.builder()
                    .date(entry.getKey())
                    .time("1200")
                    .tmp(parseIntSafe(v.get("TMP")))
                    .pop(parseIntSafe(v.get("POP")))
                    .sky(parseIntSafe(v.get("SKY")))
                    .pty(parseIntSafe(v.get("PTY")))
                    .build());
        }
        return result;
    }


    // 중기예보 (getMidLandFcst + getMidTa) - 4~7일치

    private List<WeatherResponseDto> getMidTermForecast(String region, int shortTermSize) {
        String landCode = MID_LAND_CODE.getOrDefault(region, "11B00000");
        String taCode   = MID_TA_CODE.getOrDefault(region,   "11B10101");

        // 중기예보 발표 기준시각: 06시 또는 18시
        LocalDateTime now = LocalDateTime.now();
        String tmFc = getMidBaseTime(now);

        String landUrl = "https://apihub.kma.go.kr/api/typ02/openApi/MidFcstInfoService/getMidLandFcst"
                + "?numOfRows=10&pageNo=1&dataType=JSON"
                + "&regId=" + landCode
                + "&tmFc=" + tmFc
                + "&authKey=" + apiKey;

        String taUrl = "https://apihub.kma.go.kr/api/typ02/openApi/MidFcstInfoService/getMidTa"
                + "?numOfRows=10&pageNo=1&dataType=JSON"
                + "&regId=" + taCode
                + "&tmFc=" + tmFc
                + "&authKey=" + apiKey;

        try {
            String landRaw = restTemplate.getForObject(landUrl, String.class);
            String taRaw   = restTemplate.getForObject(taUrl,   String.class);
            return parseMidTerm(landRaw, taRaw, shortTermSize);
        } catch (Exception e) {
            // 중기예보 실패 시 빈 리스트 반환 (단기만이라도 보여줌)
            return Collections.emptyList();
        }
    }

    private List<WeatherResponseDto> parseMidTerm(String landRaw, String taRaw, int shortTermSize) throws Exception {
        // 중기육상예보 파싱 → 강수확률(rnSt), 날씨(wf) by day index
        JsonNode landItem = objectMapper.readTree(landRaw)
                .path("response").path("body").path("items").path("item");
        JsonNode taItem   = objectMapper.readTree(taRaw)
                .path("response").path("body").path("items").path("item");

        if (!landItem.isArray() || landItem.isEmpty()) return Collections.emptyList();
        if (!taItem.isArray()   || taItem.isEmpty())   return Collections.emptyList();

        JsonNode land = landItem.get(0);
        JsonNode ta   = taItem.get(0);

        // 중기예보는 3일 후부터 10일 후까지 제공
        // shortTermSize=3이면 4일차(index 4)부터 7일차(index 7)까지 추출
        List<WeatherResponseDto> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int day = shortTermSize + 1; day <= 7; day++) {
            String rnSt = getTextSafe(land, "rnSt" + day);       // 강수확률
            String wf   = getTextSafe(land, "wf"   + day);       // 날씨 텍스트 (맑음/구름많음 등)
            String taMin = getTextSafe(ta, "taMin" + day);        // 최저기온
            String taMax = getTextSafe(ta, "taMax" + day);        // 최고기온

            int pop = parseIntSafe(rnSt);
            int tmp = (parseIntSafe(taMin) + parseIntSafe(taMax)) / 2;  // 최고+최저 평균
            int sky = wfToSky(wf);
            int pty = pop >= 60 ? 1 : 0;  // 강수확률 60% 이상이면 비로 표시

            String date = today.plusDays(day).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            result.add(WeatherResponseDto.builder()
                    .date(date)
                    .time("1200")
                    .tmp(tmp)
                    .pop(pop)
                    .sky(sky)
                    .pty(pty)
                    .build());
        }
        return result;
    }

    // 중기예보 날씨텍스트 → SKY 코드 변환
    private int wfToSky(String wf) {
        if (wf == null) return 1;
        if (wf.contains("맑음"))     return 1;
        if (wf.contains("구름많음")) return 3;
        if (wf.contains("흐림"))     return 4;
        return 3; // 기본값
    }

    // 중기예보 발표 기준시각: 0600 또는 1800
    private String getMidBaseTime(LocalDateTime now) {
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return now.getHour() >= 18 ? date + "1800" : date + "0600";
    }

    // 단기예보 발표 기준시각 계산
    private LocalDateTime getBaseDateTime(LocalDateTime now) {
        int[] hours = {2, 5, 8, 11, 14, 17, 20, 23};
        LocalDateTime candidate = now.minusMinutes(10);
        int h = candidate.getHour();
        int base = 23;
        for (int t : hours) { if (h >= t) base = t; }
        LocalDateTime result = candidate.withHour(base).withMinute(0).withSecond(0).withNano(0);
        if (h < 2) result = result.minusDays(1).withHour(23);
        return result;
    }

    private String getTextSafe(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() ? "0" : n.asText();
    }

    private int parseIntSafe(String val) {
        if (val == null) return 0;
        try { return (int) Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    // ===================== E. 날짜별 날씨 =====================

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 기상청 평년값(2011~2020) 월별 최저/최고 기온 근사 — 11일 이상 예보 없는 구간에 쓴다.
    // 지금은 전국 공통값이고, 지역별로 나눠야 하면 지역×월 테이블로 확장한다.
    private static final int[][] MONTHLY_NORMAL = {
            {-6, 3}, {-4, 6}, {1, 11}, {7, 18}, {13, 23}, {18, 27},
            {22, 29}, {23, 30}, {17, 26}, {10, 20}, {3, 12}, {-3, 5}
    };

    /** 남은 일수 → 예보 종류. 0~2 단기 / 3~10 중기 / 11+ 평년. */
    static String sourceFor(long daysAhead) {
        if (daysAhead <= 2)  return "SHORT";
        if (daysAhead <= 10) return "MID";
        return "NORMAL";
    }

    @Override
    public DayWeather getDayWeather(String region, LocalDate date) {
        List<DayWeather> one = getDailyRange(region, date, date);
        return one.isEmpty() ? normalDay(date) : one.get(0);
    }

    @Override
    public List<DayWeather> getDailyRange(String region, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) return Collections.emptyList();
        LocalDate today = LocalDate.now();

        // 단기(0~2)·중기(3~10) 는 한 번씩만 호출해 날짜→값 맵으로 만든다 (실시간 호출 이력 유지).
        Map<String, DayWeather> shortMap = safeShortDaily(region);
        Map<String, DayWeather> midMap   = safeMidDaily(region);

        List<DayWeather> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long ahead = ChronoUnit.DAYS.between(today, d);
            String key = d.format(YMD);
            String src = sourceFor(ahead);
            DayWeather dw = switch (src) {
                case "SHORT" -> shortMap.get(key);
                case "MID"   -> midMap.get(key);
                default      -> null;
            };
            // 예보가 비면(호출 실패·경계일) 평년값으로 폴백
            result.add(dw != null ? dw : normalDay(d));
        }
        return result;
    }

    /** 11일+ 또는 예보 없음 — 평년값. rainProb 는 null(모름). */
    private DayWeather normalDay(LocalDate date) {
        int[] mm = MONTHLY_NORMAL[date.getMonthValue() - 1];
        return DayWeather.builder()
                .date(date.format(YMD)).source("NORMAL")
                .tempMin(mm[0]).tempMax(mm[1])
                .rainProb(null).sky("평년").rainExpected(false)
                .build();
    }

    private Map<String, DayWeather> safeShortDaily(String region) {
        try { return parseShortDaily(fetchShortRaw(region)); }
        catch (Exception e) { return Collections.emptyMap(); }
    }

    private Map<String, DayWeather> safeMidDaily(String region) {
        try { return parseMidDaily(region); }
        catch (Exception e) { return Collections.emptyMap(); }
    }

    private String fetchShortRaw(String region) {
        int[] grid = REGION_GRID.getOrDefault(region, REGION_GRID.get("서울"));
        LocalDateTime base = getBaseDateTime(LocalDateTime.now());
        String url = "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst"
                + "?pageNo=1&numOfRows=1000&dataType=JSON"
                + "&base_date=" + base.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "&base_time=" + base.format(DateTimeFormatter.ofPattern("HHmm"))
                + "&nx=" + grid[0] + "&ny=" + grid[1] + "&authKey=" + apiKey;
        return restTemplate.getForObject(url, String.class);
    }

    /** 단기예보 → 날짜별 최저(TMN)/최고(TMX)/강수확률(최댓값)/하늘상태(정오). */
    private Map<String, DayWeather> parseShortDaily(String raw) throws Exception {
        JsonNode items = objectMapper.readTree(raw)
                .path("response").path("body").path("items").path("item");

        Map<String, Integer> tmn = new HashMap<>(), tmx = new HashMap<>();
        Map<String, Integer> popMax = new HashMap<>(), skyNoon = new HashMap<>();
        Map<String, Boolean> rain = new HashMap<>();

        for (JsonNode it : items) {
            String date = it.path("fcstDate").asText();
            String time = it.path("fcstTime").asText();
            String cat  = it.path("category").asText();
            String val  = it.path("fcstValue").asText();
            switch (cat) {
                case "TMN" -> tmn.put(date, parseIntSafe(val));
                case "TMX" -> tmx.put(date, parseIntSafe(val));
                case "POP" -> popMax.merge(date, parseIntSafe(val), Math::max);
                case "SKY" -> { if ("1200".equals(time)) skyNoon.put(date, parseIntSafe(val)); }
                case "PTY" -> { if (parseIntSafe(val) > 0) rain.put(date, true); }
                default -> {}
            }
        }

        Map<String, DayWeather> out = new LinkedHashMap<>();
        for (String date : tmx.keySet()) {
            long ahead = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date, YMD));
            int pop = popMax.getOrDefault(date, 0);
            out.put(date, DayWeather.builder()
                    .date(date).source(sourceFor(ahead))
                    .tempMin(tmn.getOrDefault(date, tmx.get(date)))
                    .tempMax(tmx.get(date))
                    .rainProb(pop)
                    .sky(skyText(skyNoon.getOrDefault(date, 1)))
                    .rainExpected(rain.getOrDefault(date, false) || pop >= 60)
                    .build());
        }
        return out;
    }

    /** 중기예보 → 3~10일 후 날짜별 최저/최고/강수확률/하늘상태. */
    private Map<String, DayWeather> parseMidDaily(String region) throws Exception {
        String landCode = MID_LAND_CODE.getOrDefault(region, "11B00000");
        String taCode   = MID_TA_CODE.getOrDefault(region,   "11B10101");
        String tmFc = getMidBaseTime(LocalDateTime.now());

        String landUrl = "https://apihub.kma.go.kr/api/typ02/openApi/MidFcstInfoService/getMidLandFcst"
                + "?numOfRows=10&pageNo=1&dataType=JSON&regId=" + landCode + "&tmFc=" + tmFc + "&authKey=" + apiKey;
        String taUrl = "https://apihub.kma.go.kr/api/typ02/openApi/MidFcstInfoService/getMidTa"
                + "?numOfRows=10&pageNo=1&dataType=JSON&regId=" + taCode + "&tmFc=" + tmFc + "&authKey=" + apiKey;

        JsonNode land = firstItem(restTemplate.getForObject(landUrl, String.class));
        JsonNode ta   = firstItem(restTemplate.getForObject(taUrl,   String.class));
        if (land == null || ta == null) return Collections.emptyMap();

        LocalDate today = LocalDate.now();
        Map<String, DayWeather> out = new LinkedHashMap<>();
        for (int day = 3; day <= 10; day++) {
            // 중기육상예보는 8일차부터 오전/오후 구분이 없어 wf{n} 하나로 온다. rnSt 도 마찬가지.
            String wf   = pick(land, "wf" + day + "Am", "wf" + day);
            String rnSt = pick(land, "rnSt" + day + "Am", "rnSt" + day);
            String taMin = getTextSafe(ta, "taMin" + day);
            String taMax = getTextSafe(ta, "taMax" + day);

            int pop = parseIntSafe(rnSt);
            String date = today.plusDays(day).format(YMD);
            out.put(date, DayWeather.builder()
                    .date(date).source("MID")
                    .tempMin(parseIntSafe(taMin)).tempMax(parseIntSafe(taMax))
                    .rainProb(pop).sky(skyText(wfToSky(wf)))
                    .rainExpected(pop >= 60)
                    .build());
        }
        return out;
    }

    private JsonNode firstItem(String raw) throws Exception {
        JsonNode item = objectMapper.readTree(raw)
                .path("response").path("body").path("items").path("item");
        return (item.isArray() && !item.isEmpty()) ? item.get(0) : (item.isObject() ? item : null);
    }

    /** 여러 필드명 후보 중 값이 있는 첫 번째 반환 (중기예보 오전/오후 vs 단일 필드 대응). */
    private String pick(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (!n.isMissingNode() && !n.asText().isBlank()) return n.asText();
        }
        return "";
    }

    private String skyText(int sky) {
        return switch (sky) {
            case 1 -> "맑음";
            case 3 -> "구름많음";
            case 4 -> "흐림";
            default -> "구름많음";
        };
    }
}