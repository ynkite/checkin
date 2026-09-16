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

    /** 화면이 한 번에 보는 날수. 단기 3일 + 중기 4일. */
    private static final int FORECAST_DAYS = 7;

    @Override
    public List<WeatherResponseDto> getForecast(String region) {
        /* 전에는 단기 목록 뒤에 중기를 이어 붙였는데, 중기를 4일차부터
           채워서 3일차가 빠진 채로 일곱 칸이 나왔다. 날짜로 채운다 —
           같은 날짜에 단기와 중기가 다 있으면 단기가 이긴다. */
        LocalDate today = LocalDate.now();
        List<DayWeather> days = getDailyRange(region, today, today.plusDays(FORECAST_DAYS - 1));

        List<WeatherResponseDto> result = new ArrayList<>(days.size());
        for (DayWeather d : days) {
            int min = d.getTempMin() != null ? d.getTempMin() : 0;
            int max = d.getTempMax() != null ? d.getTempMax() : min;
            result.add(WeatherResponseDto.builder()
                    .date(d.getDate())
                    .time("1200")
                    .tmp(Math.round((min + max) / 2f))
                    .pop(d.getRainProb() != null ? d.getRainProb() : 0)
                    .sky(skyCode(d.getSky()))
                    .pty(d.isRainExpected() ? 1 : 0)
                    .build());
        }
        return result;
    }

    /** 하늘상태 글자 → 단기예보 SKY 코드. 화면이 아이콘을 고를 때 쓴다. */
    private int skyCode(String sky) {
        if (sky == null) return 3;
        if (sky.contains("맑음")) return 1;
        if (sky.contains("흐림")) return 4;
        return 3;
    }


    /* getForecast 가 getDailyRange 로 넘어가면서
       여기 있던 단기·중기 복사팝 네 개를 지웠다.
       날짜별 함수(parseShortDaily · parseMidDaily)만 남긴다. */

    // 중기예보 날씨텍스트 → SKY 코드 변환
    private int wfToSky(String wf) {
        if (wf == null) return 1;
        if (wf.contains("맑음"))     return 1;
        if (wf.contains("구름많음")) return 3;
        if (wf.contains("흐림"))     return 4;
        return 3; // 기본값
    }

    /* 중기예보는 06시와 18시에 나온다.
       06시 전에 오늘 06시 발표를 달라고 하면 빈 응답이 온다 —
       그러면 4일차부터가 통째로 사라진다. 그때는 어제 18시 발표를 쓴다. */
    private LocalDateTime midBase(LocalDateTime now) {
        LocalDateTime t = now.withMinute(0).withSecond(0).withNano(0);
        if (now.getHour() >= 18) return t.withHour(18);
        if (now.getHour() >= 6)  return t.withHour(6);
        return t.minusDays(1).withHour(18);
    }

    private String getMidBaseTime(LocalDateTime now) {
        LocalDateTime b = midBase(now);
        return b.format(DateTimeFormatter.ofPattern("yyyyMMddHH")) + "00";
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

    /* 없는 값을 "0" 으로 바꾸지 않는다. 0은 영도라는 말이지
       모른다는 말이 아니다. 부를 쏪이 판단한다. */
    private String getTextSafe(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? "" : n.asText();
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
            /* 종류로 갈라 고르지 않는다. 단기가 있으면 단기가 이긴다 —
               중기예보는 단기가 덮는 사흘을 뱈 값으로 내려준다.
               그걸 그대로 쓰면 사흘째가 0°C 로 보인다. */
            DayWeather dw = shortMap.get(key);
            if (dw == null) dw = midMap.get(key);
            // 예보가 아에 없으면(열흘 뒤·호출 실패) 평년값으로 말한다
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
        LocalDateTime base = midBase(LocalDateTime.now());
        String tmFc = getMidBaseTime(LocalDateTime.now());

        String landUrl = "https://apihub.kma.go.kr/api/typ02/openApi/MidFcstInfoService/getMidLandFcst"
                + "?numOfRows=10&pageNo=1&dataType=JSON&regId=" + landCode + "&tmFc=" + tmFc + "&authKey=" + apiKey;
        String taUrl = "https://apihub.kma.go.kr/api/typ02/openApi/MidFcstInfoService/getMidTa"
                + "?numOfRows=10&pageNo=1&dataType=JSON&regId=" + taCode + "&tmFc=" + tmFc + "&authKey=" + apiKey;

        JsonNode land = firstItem(restTemplate.getForObject(landUrl, String.class));
        JsonNode ta   = firstItem(restTemplate.getForObject(taUrl,   String.class));
        if (land == null || ta == null) return Collections.emptyMap();

        /* dayN 은 발표일로부터 N일 뒤다. 오늘 기준으로 매기면
           어제 18시 발표를 쓸 때 하루씩 밀린다. */
        LocalDate from = base.toLocalDate();
        Map<String, DayWeather> out = new LinkedHashMap<>();
        for (int day = 3; day <= 10; day++) {
            // 중기육상예보는 8일차부터 오전/오후 구분이 없어 wf{n} 하나로 온다. rnSt 도 마찬가지.
            String wf   = pick(land, "wf" + day + "Am", "wf" + day);
            String rnSt = pick(land, "rnSt" + day + "Am", "rnSt" + day);
            String taMin = getTextSafe(ta, "taMin" + day);
            String taMax = getTextSafe(ta, "taMax" + day);

            /* 단기가 덮는 날짜는 중기 칸이 미음으로 온다.
               getTextSafe 가 "0" 을 돌려서 0°C 로 나갔다. 모를 때는 담지 않는다. */
            if (taMin.isBlank() || taMax.isBlank() || "0".equals(taMin) && "0".equals(taMax)) continue;

            int pop = parseIntSafe(rnSt);
            String date = from.plusDays(day).format(YMD);
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