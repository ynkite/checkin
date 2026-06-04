package idusw.sbb.triplinker.domain.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.triplinker.domain.weather.dto.WeatherResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        // ── 1. 단기예보 (오늘~3일차) ──
        List<WeatherResponseDto> shortTerm = getShortTermForecast(region);

        // ── 2. 중기예보 (4~7일차) ──
        List<WeatherResponseDto> midTerm = getMidTermForecast(region, shortTerm.size());

        // ── 3. 합치기 ──
        List<WeatherResponseDto> result = new ArrayList<>(shortTerm);
        result.addAll(midTerm);
        return result;
    }

    // ────────────────────────────────────────────
    // 단기예보 (getVilageFcst) → 오늘~3일치
    // ────────────────────────────────────────────
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

    // ────────────────────────────────────────────
    // 중기예보 (getMidLandFcst + getMidTa) → 4~7일치
    // ────────────────────────────────────────────
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
}