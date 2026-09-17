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
    private final ObjectMapper objectMapper;

    /** 실시간으로 열 수 있는 여행. 끝난 지 이틀 넘은 것은 뺀다 */
    public List<Map<String, Object>> openableTrips() {
        Long userId = currentUserId();
        if (userId == null) return List.of();

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (TravelPlan p : planRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
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
        String head = headline(ph, next, crowd);

        return new LiveSnapshot(
                tripId,
                plan.getTitle() != null ? plan.getTitle() : plan.getDestination(),
                plan.getDestination(),
                String.valueOf(dayDate),
                dayNo, ph, head,
                here, next, stops,
                crowd,
                Map.of(),            /* 날씨는 화면이 이미 있는 /api/maps/weather 로 따로 묻는다 */
                actions(crowd, next)
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

    /** 한 문장. 운전 중에 들어도 되는 길이로 쓴다 */
    private String headline(String phase, Map<String, Object> next, Map<String, Object> crowd) {
        String name = String.valueOf(next.getOrDefault("name", ""));
        if (name.isBlank()) return "오늘 남은 일정이 없습니다.";

        if ("BEFORE".equals(phase)) return "아직 여행 전입니다. 첫 일정은 " + name + "입니다.";
        if ("AFTER".equals(phase))  return "지난 여행입니다. 기록만 볼 수 있습니다.";

        Object rate = crowd.get("rate");
        String label = String.valueOf(crowd.getOrDefault("levelLabel", ""));
        if (rate instanceof Number r && r.intValue() >= 70) {
            return name + "이 지금 " + label + "입니다. 순서를 바꿔 볼까요.";
        }
        return "다음은 " + name + "입니다.";
    }

    /** 지금 할 수 있는 것. 화면이 큰 버튼으로 만든다 */
    private List<Map<String, Object>> actions(Map<String, Object> crowd, Map<String, Object> next) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object rate = crowd.get("rate");
        boolean busy = rate instanceof Number r && r.intValue() >= 70;

        if (busy) {
            out.add(act("swap", "순서 바꾸기", "붐비는 곳을 뒤로 미룹니다"));
            out.add(act("quiet", "다른 곳으로", "그 시각에 한적한 곳을 찾습니다"));
        }
        if (next.get("lat") instanceof Number) {
            out.add(act("navi", "길 안내", "다음 장소까지 안내를 켭니다"));
        }
        out.add(act("map", "전체 일정", "지도에서 손으로 고칩니다"));
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
