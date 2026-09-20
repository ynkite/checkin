package idusw.sbb.checkin.domain.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.crowd.CrowdService;
import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * 「지금 어떤가」를 한 번에 낸다.
 *
 * 지도 화면과 뭐가 다른가 —
 *   지도  전체 일정을 펼쳐 놓고 손으로 옮긴다. 앉아서 쓰는 화면이다
 *   실시간 지금 한 구간만 크게. 서서·움직이면서 보는 화면이다
 *
 * 여행 전날에도 들어올 수 있어야 한다. 「내일 이 시각이면」을 미리
 * 보려는 사람이 있다. 그래서 date 를 받아 그 날짜로 답한다.
 *
 * 값이 없으면 없다고 말한다. 0 으로 채우지 않는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LiveService {

    private final TravelPlanRepository planRepository;
    private final CrowdService crowdService;
    private final idusw.sbb.checkin.domain.weather.service.WeatherService weatherService;
    private final ObjectMapper objectMapper;

    /** 이 확률부터 비로 본다. 감지 엔진과 같은 설정값을 읽는다 —
        두 곳이 다른 값을 쓰면 화면과 알림이 서로 다른 말을 한다 */
    @org.springframework.beans.factory.annotation.Value("${detection.weather.rain-prob-threshold:60}")
    private int rainProbThreshold;

    /** 실시간으로 열 수 있는 여행. 끝난 지 이틀 넘은 것은 뺀다 */
    public List<Map<String, Object>> openableTrips() {
        Long userId = currentUserId();
        if (userId == null) return List.of();

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (TravelPlan p : planRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            /* 「내 여행」과 같은 기준으로 거른다. 거기는 FIXED 만 보여 준다.
               여기만 DRAFT 까지 보여 주면, 만들다 만 일정이 실시간 목록에는
               떠 있는데 「내 여행」에서는 안 보인다. 지우러 갈 데가 없어진다.
               두 화면이 같은 여행을 두고 다른 말을 하면 그게 더 큰 문제다.
               초대받아 저장한 것(INVITED)은 경로가 없어서 아래에서 걸린다. */
            if (!"FIXED".equals(p.getStatus())) continue;
            if (p.getStartDate() == null) continue;
            LocalDate end = p.getEndDate() != null ? p.getEndDate() : p.getStartDate();
            if (end.plusDays(2).isBefore(today)) continue;          /* 지난 여행 */
            if (routeOf(p) == null) continue;                        /* 경로가 없으면 열 게 없다 */

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tripId", p.getId());
            m.put("title", p.getTitle() != null ? p.getTitle() : p.getDestination());
            m.put("destination", p.getDestination());
            m.put("startDate", String.valueOf(p.getStartDate()));
            m.put("endDate", String.valueOf(end));
            m.put("phase", phase(p, today));
            out.add(m);
        }
        return out;
    }

    public LiveSnapshot snapshot(Long tripId, Double lat, Double lng, LocalDate date) {
        TravelPlan plan = planRepository.findById(tripId).orElse(null);
        if (plan == null) return LiveSnapshot.empty(tripId, "여행을 찾지 못했습니다.");

        JsonNode route = routeOf(plan);
        if (route == null) return LiveSnapshot.empty(tripId, "이 여행에는 아직 경로가 없습니다.");

        LocalDate base = date != null ? date : LocalDate.now();
        String ph = phase(plan, LocalDate.now());

        /* 며칠째인가. 여행 전이면 첫날을 보여 준다 — 「내일 이 시각이면」 */
        int dayNo = 1;
        if (plan.getStartDate() != null) {
            long diff = java.time.temporal.ChronoUnit.DAYS.between(plan.getStartDate(), base);
            dayNo = (int) Math.max(1, Math.min(route.size(), diff + 1));
        }
        JsonNode day = route.get(dayNo - 1);
        LocalDate dayDate = plan.getStartDate() != null
                ? plan.getStartDate().plusDays(dayNo - 1) : base;

        List<Map<String, Object>> stops = stopsOf(day);
        if (stops.isEmpty()) return LiveSnapshot.empty(tripId, "그날 들를 곳이 없습니다.");

        /* 지금 어디쯤인가 — 시각으로 가른다. 좌표가 있으면 그걸 우선한다 */
        int idx = liveIndex(stops, base.equals(LocalDate.now()) ? LocalTime.now() : null, lat, lng);
        Map<String, Object> here = idx >= 0 ? stops.get(idx) : Map.of();
        Map<String, Object> next = (idx + 1) < stops.size() ? stops.get(idx + 1)
                : (idx < 0 && !stops.isEmpty() ? stops.get(0) : Map.of());

        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).put("passed", i < idx);
            stops.get(i).put("now", i == idx);
        }

        Map<String, Object> crowd = crowdOf(plan, next, dayDate);
        Map<String, Object> weather = weatherOf(plan, next, dayDate);
        String head = headline(ph, next, crowd, weather);

        return new LiveSnapshot(
                tripId,
                plan.getTitle() != null ? plan.getTitle() : plan.getDestination(),
                plan.getDestination(),
                String.valueOf(dayDate),
                dayNo, ph, head,
                here, next, stops,
                crowd,
                weather,
                actions(tripId, dayNo, crowd, weather, next)
        );
    }

    /* ── 바닥 ──────────────────────────────────────────────── */

    private String phase(TravelPlan p, LocalDate today) {
        if (p.getStartDate() == null) return "BEFORE";
        LocalDate end = p.getEndDate() != null ? p.getEndDate() : p.getStartDate();
        if (today.isBefore(p.getStartDate())) return "BEFORE";
        if (today.isAfter(end)) return "AFTER";
        return "TODAY";
    }

    private JsonNode routeOf(TravelPlan p) {
        String json = p.getDraftRouteJson() != null && !p.getDraftRouteJson().isBlank()
                ? p.getDraftRouteJson() : p.getRouteJson();
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode n = objectMapper.readTree(json);
            return (n.isArray() && !n.isEmpty()) ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> stopsOf(JsonNode day) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode p : day.path("places")) {
            if (p.hasNonNull("transit")) continue;
            String name = p.path("name").asText("");
            if (name.isBlank()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("time", p.path("time").asText(""));
            m.put("type", p.path("type").asText(""));
            m.put("sub", p.path("sub").asText(""));
            if (p.hasNonNull("lat")) m.put("lat", p.path("lat").asDouble());
            if (p.hasNonNull("lng")) m.put("lng", p.path("lng").asDouble());
            if (p.hasNonNull("crowd")) m.put("crowd", p.path("crowd").asInt());
            if (p.hasNonNull("crowdLabel")) m.put("crowdLabel", p.path("crowdLabel").asText());
            out.add(m);
        }
        return out;
    }

    /**
     * 지금 몇 번째 정거장인가.
     * 좌표가 있으면 제일 가까운 곳, 없으면 시각으로 가른다.
     * 시각도 없으면(전날 미리보기) -1 — 아직 출발 전이라는 뜻이다.
     */
    private int liveIndex(List<Map<String, Object>> stops, LocalTime now, Double lat, Double lng) {
        if (lat != null && lng != null) {
            int best = -1; double bestD = Double.MAX_VALUE;
            for (int i = 0; i < stops.size(); i++) {
                Object a = stops.get(i).get("lat"), b = stops.get(i).get("lng");
                if (!(a instanceof Number) || !(b instanceof Number)) continue;
                double d = Math.hypot(((Number) a).doubleValue() - lat,
                                      ((Number) b).doubleValue() - lng);
                if (d < bestD) { bestD = d; best = i; }
            }
            /* 0.02도 = 대략 2km. 그보다 멀면 「그 동선 위에 있다」고 볼 수 없다 */
            if (best >= 0 && bestD <= 0.02) return best;
        }
        if (now == null) return -1;

        int idx = -1;
        for (int i = 0; i < stops.size(); i++) {
            LocalTime t = parseTime(String.valueOf(stops.get(i).get("time")));
            if (t != null && !t.isAfter(now)) idx = i;
        }
        return idx;
    }

    private LocalTime parseTime(String s) {
        if (s == null) return null;
        var m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s);
        if (!m.find()) return null;
        try {
            return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> crowdOf(TravelPlan plan, Map<String, Object> next, LocalDate date) {
        Object name = next.get("name");
        if (!(name instanceof String n) || n.isBlank()) return Map.of();

        /* 동선에 이미 붙어 있으면 그걸 쓴다 — 저장할 때 붙여 뒀다 */
        if (next.get("crowd") instanceof Number c) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("placeName", n);
            m.put("rate", c.intValue());
            m.put("levelLabel", next.getOrDefault("crowdLabel", ""));
            m.put("source", "TOUR");
            return m;
        }

        AreaCode.Area a = AreaCode.find(plan.getDestination());
        if (a == null || !AreaCode.hasCrowdData(a)) return Map.of();
        try {
            CrowdForecast f = crowdService.forecast(a.areaCd(), a.signguCd(), n, date);
            if (f == null || f.rate() == null) return Map.of();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("placeName", f.placeName());
            m.put("rate", Math.round(f.rate()));
            m.put("levelLabel", f.levelLabel());
            m.put("levelKey", f.levelKey());
            m.put("source", "TOUR");
            return m;
        } catch (Exception e) {
            log.warn("[실시간] 혼잡도 조회 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 그 날짜의 날씨. 동선에 이미 붙어 있으면 그걸 쓴다 — 저장할 때 붙여 뒀다.
     *
     * 없으면 한 번 조회한다. 예보 격자가 없는 여행지면 빈 값을 준다 —
     * WeatherServiceImpl 은 모르는 지역을 서울 격자로 떨어뜨린다. 그걸 그대로 쓰면
     * 「여수인데 서울 날씨」가 실시간 화면에 뜬다.
     *
     * outdoorRisk 는 다음 정거장이 비를 맞는 곳인지다. 이게 있어야
     * 「비 오니 실내로」를 지금 할 수 있는 것으로 내밀 수 있다.
     */
    private Map<String, Object> weatherOf(TravelPlan plan, Map<String, Object> next, LocalDate date) {
        Map<String, Object> m = new LinkedHashMap<>();

        /* 저장된 동선에 붙어 있는 값이 먼저다. API 를 다시 부르지 않는다 */
        Object label = next.get("wxLabel");
        if (label instanceof String s && !s.isBlank()) {
            m.put("label", s);
            if (next.get("wx") instanceof Number rp) m.put("rainProb", rp.intValue());
            if (next.get("wxSource") instanceof String src) m.put("source", src);
            m.put("outdoorRisk", Boolean.TRUE.equals(next.get("outdoorRisk")));
            return m;
        }

        String region = idusw.sbb.checkin.domain.weather.WeatherRegion.of(plan.getDestination());
        if (region == null) return Map.of();   /* 예보를 받을 수 없는 지역 */

        try {
            var dw = weatherService.getDayWeather(region, date);
            if (dw == null) return Map.of();

            if (dw.getSky() != null && !dw.getSky().isBlank()) m.put("sky", dw.getSky());
            if (dw.getTempMin() != null) m.put("tempMin", dw.getTempMin());
            if (dw.getTempMax() != null) m.put("tempMax", dw.getTempMax());
            if (dw.getRainProb() != null) m.put("rainProb", dw.getRainProb());
            m.put("rain", dw.isRainExpected());
            m.put("source", dw.getSource());

            String name = String.valueOf(next.getOrDefault("name", ""));
            String type = String.valueOf(next.getOrDefault("type", ""));
            /* rainExpected 플래그만 보면 「강수확률 70%인데 플래그는 false」인 예보를 놓친다.
               감지 엔진과 같은 규칙으로 판정한다 */
            boolean rainy = idusw.sbb.checkin.domain.detection.DetectionRules
                    .weather(dw.isRainExpected(), dw.getRainProb(), rainProbThreshold).triggered();
            m.put("outdoorRisk", rainy
                    && idusw.sbb.checkin.domain.weather.PlaceOutdoor.is(name, type));

            return m.isEmpty() ? Map.of() : m;

        } catch (Exception e) {
            log.warn("[실시간] 날씨 조회 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 한 문장. 운전 중에 들어도 되는 길이로 쓴다 */
    private String headline(String phase, Map<String, Object> next,
                            Map<String, Object> crowd, Map<String, Object> weather) {
        String name = String.valueOf(next.getOrDefault("name", ""));
        if (name.isBlank()) return "오늘 남은 일정이 없습니다.";

        if ("BEFORE".equals(phase)) return "아직 여행 전입니다. 첫 일정은 " + name + "입니다.";
        if ("AFTER".equals(phase))  return "지난 여행입니다. 기록만 볼 수 있습니다.";

        /* 비가 먼저다. 붐비는 건 기다리면 풀리지만 비는 안 그렇다 */
        if (Boolean.TRUE.equals(weather.get("outdoorRisk"))) {
            Object rp = weather.get("rainProb");
            String how = rp instanceof Number n ? "비 올 확률이 " + n.intValue() + "%입니다" : "비 예보가 있습니다";
            return name + "은 야외입니다. " + how + ". 실내로 바꿔 볼까요.";
        }

        Object rate = crowd.get("rate");
        String label = String.valueOf(crowd.getOrDefault("levelLabel", ""));
        if (rate instanceof Number r && r.intValue() >= 70) {
            return name + "이 지금 " + label + "입니다. 순서를 바꿔 볼까요.";
        }
        return "다음은 " + name + "입니다.";
    }

    /**
     * 지금 할 수 있는 것. 화면이 큰 버튼으로 만든다.
     *
     * 각 갈래에 어디를 부르면 되는지까지 담는다. 전에는 key 만 내려서 화면이
     * 스스로 알아내야 했다 — 그래서 아무것도 동작에 붙어 있지 않았다.
     *
     * oneClick 이 true 면 서버가 그 요청 하나로 끝낸다. false 면 무엇을 바꿀지
     * 사람이 골라야 해서 지도 화면을 거친다 — 순서 바꾸기와 장소 교체가 그렇다.
     * 하나를 강요하지 않는다. 늘 갈래를 준다.
     */
    private List<Map<String, Object>> actions(Long tripId, int dayNo,
                                              Map<String, Object> crowd,
                                              Map<String, Object> weather,
                                              Map<String, Object> next) {
        List<Map<String, Object>> out = new ArrayList<>();
        String base = "/api/trips/" + tripId + "/routes";

        /* 비 오는 날 야외 — 서버가 그날을 실내로 다시 짠다. 한 번으로 끝난다 */
        if (Boolean.TRUE.equals(weather.get("outdoorRisk"))) {
            Map<String, Object> a = act("indoor", "실내로 바꾸기", "그날 야외 일정을 실내로 다시 짭니다");
            a.put("method", "POST");
            a.put("endpoint", base + "/indoor-replace?day=" + dayNo);
            a.put("oneClick", true);
            out.add(a);
        }

        Object rate = crowd.get("rate");
        if (rate instanceof Number r && r.intValue() >= 70) {
            Map<String, Object> swap = act("swap", "순서 바꾸기", "붐비는 곳을 뒤로 미룹니다");
            swap.put("method", "POST");
            swap.put("endpoint", base + "/reorder");
            swap.put("oneClick", false);   /* 바뀐 순서를 본문에 담아야 한다 */
            out.add(swap);

            Map<String, Object> quiet = act("quiet", "다른 곳으로", "그 시각에 한적한 곳을 찾습니다");
            quiet.put("method", "POST");
            quiet.put("endpoint", base + "/replace");
            quiet.put("oneClick", false);  /* 어느 곳을 무엇으로 바꿀지 골라야 한다 */
            out.add(quiet);
        }

        if (next.get("lat") instanceof Number) {
            Map<String, Object> navi = act("navi", "길 안내", "다음 장소까지 안내를 켭니다");
            navi.put("oneClick", false);   /* 브라우저에서 위치를 잡는다 */
            out.add(navi);
        }

        Map<String, Object> map = act("map", "전체 일정", "지도에서 손으로 고칩니다");
        map.put("oneClick", false);
        out.add(map);
        return out;
    }

    private Map<String, Object> act(String key, String label, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key); m.put("label", label); m.put("note", note);
        return m;
    }

    private Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof CustomUserDetails u)) return null;
        return u.getUserId();
    }
}
