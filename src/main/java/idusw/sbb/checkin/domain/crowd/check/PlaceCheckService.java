package idusw.sbb.checkin.domain.crowd.check;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.crowd.CrowdLevel;
import idusw.sbb.checkin.domain.crowd.CrowdService;
import idusw.sbb.checkin.domain.crowd.SkRealtimeCrowdClient;
import idusw.sbb.checkin.domain.crowd.check.PlaceCheck.Finding;
import idusw.sbb.checkin.domain.crowd.check.PlaceCheck.Kind;
import idusw.sbb.checkin.domain.crowd.check.PlaceCheck.Status;
import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.live.LiveService;
import idusw.sbb.checkin.domain.live.LiveSnapshot;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.planshare.repository.TripMemberRepository;
import idusw.sbb.checkin.domain.route.tmap.TmapClient;
import idusw.sbb.checkin.global.apikey.PaidGate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실시간 화면 「장소 확인하기」 — 다음 정거장의 상황을 확인하고 갈래를 준다.
 *
 * 당일이면 실측을 먼저 쓴다(SK 실시간 인원 — 유료라 PaidGate 뒤, TMAP 실시간 교통).
 * 당일이 아니면 예측을 쓴다(관광공사 집중률 예측, 기상청 예보). 무엇을 근거로 했는지 늘 적는다.
 *
 * 확인 요청은 모두 {@link #check} 한 곳을 지난다. 「하루 3번」 제한은 배포 전에 여기 붙인다
 * (docs-private 배포전_필수 1번). 지금은 넣지 않는다.
 *
 * 실시간 서버(LiveService)는 고치지 않고 부르기만 한다. 다음 정거장·날씨·갈래는 거기서 받는다.
 * TMAP 은 자동차만 부른다 — 대중교통·도보는 이 계정에 안 열려 있어 403 이 오고, 키가 그날 멈춘다.
 */
@Slf4j
@Service
public class PlaceCheckService {

    static final int BUSY = 70;          // 실시간 화면 순서 바꾸기 기준과 같게
    static final int LATE_MIN = 15;      // 이만큼 늦으면 알린다

    private final TravelPlanRepository planRepository;
    private final TripMemberRepository memberRepository;
    private final LiveService liveService;
    private final CrowdService crowdService;
    private final SkRealtimeCrowdClient sk;
    private final PaidGate paidGate;
    private final TmapClient tmap;
    private Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    public PlaceCheckService(TravelPlanRepository planRepository, TripMemberRepository memberRepository,
                             LiveService liveService, CrowdService crowdService, SkRealtimeCrowdClient sk,
                             PaidGate paidGate, TmapClient tmap) {
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.liveService = liveService;
        this.crowdService = crowdService;
        this.sk = sk;
        this.paidGate = paidGate;
        this.tmap = tmap;
    }

    void setClock(Clock clock) { this.clock = clock; }

    /** 볼 수 있는 여행인가 — 주인이거나 함께하는 사람 */
    public boolean canCheck(Long tripId, Long userId) {
        if (tripId == null || userId == null) return false;
        TravelPlan plan = planRepository.findById(tripId).orElse(null);
        if (plan == null) return false;
        if (plan.getUser() != null && userId.equals(plan.getUser().getId())) return true;
        return memberRepository.findByTravelPlanId(tripId).stream()
                .anyMatch(m -> m.getUser() != null && userId.equals(m.getUser().getId()));
    }

    /** 확인 한 번. 모든 확인 요청이 여기를 지난다 */
    public PlaceCheck.Result check(Long tripId, Double lat, Double lng) {
        LocalDateTime now = LocalDateTime.now(clock);
        String checkedAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        TravelPlan plan = planRepository.findById(tripId).orElse(null);
        if (plan == null) return PlaceCheck.nothing("FORECAST", "", null, checkedAt, "여행을 찾지 못했습니다.");

        LiveSnapshot snap = liveService.snapshot(tripId, lat, lng, null);
        if ("AFTER".equals(snap.phase())) {
            return PlaceCheck.nothing("FORECAST", "", snap.date(), checkedAt, "지난 여행이라 확인할 것이 없습니다.");
        }
        Map<String, Object> next = snap.next() == null ? Map.of() : snap.next();
        if (next.get("name") == null) {
            return PlaceCheck.nothing("FORECAST", "", snap.date(), checkedAt,
                    snap.headline() != null ? snap.headline() : "남은 일정이 없습니다.");
        }

        LocalDate day = snap.date() != null ? LocalDate.parse(snap.date()) : now.toLocalDate();
        boolean live = "TODAY".equals(snap.phase()) && day.equals(now.toLocalDate());
        String mode = live ? "LIVE" : "FORECAST";
        String modeLabel = live ? "지금 상황" : day.getMonthValue() + "월 " + day.getDayOfMonth() + "일 예측";

        Map<String, Object> place = new LinkedHashMap<>();
        place.put("name", next.get("name"));
        place.put("time", next.getOrDefault("time", ""));
        place.put("type", next.getOrDefault("type", ""));
        place.put("day", snap.dayNo());

        List<Finding> findings = new ArrayList<>();
        findings.add(crowd(plan, next, day, live));
        findings.add(weather(snap.weather()));
        Object[] traffic = traffic(snap, next, lat, lng, live, now);
        findings.add((Finding) traffic[0]);

        Map<String, Map<String, Object>> actions = new LinkedHashMap<>();
        for (Map<String, Object> a : snap.actions()) actions.put(String.valueOf(a.get("key")), a);
        Map<String, Object> quiet = new LinkedHashMap<>();
        quiet.put("quietEndpoint", "/api/live/quiet?tripId=" + tripId + "&day=" + snap.dayNo()
                + "&name=" + URLEncoder.encode(String.valueOf(next.get("name")), StandardCharsets.UTF_8));
        actions.put("quiet", quiet);

        return PlaceCheck.decide(mode, modeLabel, String.valueOf(day), checkedAt, place, findings, actions,
                (Integer) traffic[1]);
    }

    /* ── 붐빔 ───────────────────────────────────────────────── */

    Finding crowd(TravelPlan plan, Map<String, Object> next, LocalDate day, boolean live) {
        String name = String.valueOf(next.get("name"));
        AreaCode.Area area = AreaCode.find(plan.getDestination());
        if (area == null || !AreaCode.hasCrowdData(area)) {
            /* 광주·전남은 집중률 자료가 아예 없다. 한적하다고 쓰면 거짓이다 */
            return new Finding(Kind.CROWD, Status.NO_DATA, false, "NONE", "혼잡도 자료 없음",
                    "이 지역은 혼잡도 자료가 없습니다.", Map.of());
        }

        String liveMiss = null;
        if (live) {
            if (!sk.ready()) liveMiss = "실시간 인원은 아직 연동 전이라";
            else if (!paidGate.allow("sk-realtime")) liveMiss = "실시간 인원은 지금 쓸 수 없어(유료 호출 닫힘 또는 오늘 상한)";
            else {
                Double lat = num(next.get("lat")), lng = num(next.get("lng"));
                SkRealtimeCrowdClient.Live l = sk.now(name, lat, lng);
                if (l != null) {
                    CrowdLevel lv = CrowdLevel.of(l.asRate());
                    boolean busy = l.asRate() >= BUSY;
                    return new Finding(Kind.CROWD, Status.OK, busy, "SK_LIVE", "SK 실시간 인원(지금 측정)",
                            (lv != null ? lv.label() : "붐빔 정도 " + Math.round(l.asRate())) + " — 지금 측정한 값입니다.",
                            PlaceCheck.values("rate", Math.round(l.asRate()), "level", lv == null ? null : lv.label(),
                                    "headcount", l.headcount()));
                }
                liveMiss = "실시간 인원을 받지 못해";
            }
        }

        try {
            CrowdForecast f = crowdService.forecast(area.areaCd(), area.signguCd(), name, day);
            String label = live ? "관광공사 집중률 예측(오늘)" : "관광공사 집중률 예측";
            if (f == null || f.rate() == null) {
                return new Finding(Kind.CROWD, Status.NO_DATA, false, "TOUR_FORECAST", label,
                        "이 장소는 혼잡도 예측이 없습니다.", Map.of());
            }
            boolean busy = f.rate() >= BUSY;
            String level = f.levelLabel() != null ? f.levelLabel() : "붐빔 정도 " + Math.round(f.rate());
            String summary = (live ? "오늘" : "그날") + " 예측은 「" + level + "」입니다.";
            if (liveMiss != null) summary = liveMiss + " 예측으로 봅니다. " + summary;
            return new Finding(Kind.CROWD, Status.OK, busy, "TOUR_FORECAST", label, summary,
                    PlaceCheck.values("rate", Math.round(f.rate()), "level", f.levelLabel()));
        } catch (Exception e) {
            log.warn("[장소 확인] 혼잡도 예측 실패: {}", e.getMessage());
            return new Finding(Kind.CROWD, Status.UNAVAILABLE, false, "TOUR_FORECAST", "관광공사 집중률 예측",
                    "혼잡도를 받아 오지 못했습니다.", Map.of());
        }
    }

    /* ── 비 ─────────────────────────────────────────────────── */

    static Finding weather(Map<String, Object> w) {
        if (w == null || w.isEmpty()) {
            return new Finding(Kind.RAIN, Status.UNAVAILABLE, false, "NONE", "기상청 예보",
                    "이 지역 날씨 예보를 받지 못했습니다.", Map.of());
        }
        String src = String.valueOf(w.getOrDefault("source", ""));
        if ("NORMAL".equals(src)) {
            return new Finding(Kind.RAIN, Status.NO_DATA, false, "KMA_NORMAL", "평년값(예보 아님)",
                    "아직 예보가 나오지 않은 날입니다. 비 여부는 확인되지 않았습니다.", Map.of());
        }
        String label = "SHORT".equals(src) ? "기상청 단기예보" : "MID".equals(src) ? "기상청 중기예보" : "기상청 예보";
        String code = "SHORT".equals(src) ? "KMA_SHORT" : "MID".equals(src) ? "KMA_MID" : "KMA";
        boolean risk = Boolean.TRUE.equals(w.get("outdoorRisk"));
        Object rp = w.get("rainProb");
        String sky = w.get("sky") != null ? String.valueOf(w.get("sky")) : (w.get("label") != null ? String.valueOf(w.get("label")) : "");
        String prob = rp instanceof Number n ? "비 올 확률 " + n.intValue() + "%" : "비 올 확률은 확인되지 않음";
        String summary = risk
                ? "다음 장소가 야외인데 비 예보가 있습니다(" + prob + ")."
                : (sky.isBlank() ? "" : sky + ", ") + prob + ".";
        return new Finding(Kind.RAIN, Status.OK, risk, code, label, summary,
                PlaceCheck.values("rainProb", rp, "sky", sky.isBlank() ? null : sky, "outdoor", risk));
    }

    /* ── 길 ─────────────────────────────────────────────────── */

    /** [Finding, 늦는 분(Integer 또는 null)] */
    Object[] traffic(LiveSnapshot snap, Map<String, Object> next, Double lat, Double lng, boolean live, LocalDateTime now) {
        if (!live) {
            return new Object[]{new Finding(Kind.TRAFFIC, Status.SKIPPED, false, "NONE", "TMAP 교통",
                    "길 상황은 여행 당일에 지금 위치로 확인합니다.", Map.of()), null};
        }
        Double toLat = num(next.get("lat")), toLng = num(next.get("lng"));
        Map<String, Object> here = snap.here() == null ? Map.of() : snap.here();
        Double fromLat = lat != null ? lat : num(here.get("lat"));
        Double fromLng = lng != null ? lng : num(here.get("lng"));
        if (toLat == null || toLng == null || fromLat == null || fromLng == null) {
            return new Object[]{new Finding(Kind.TRAFFIC, Status.SKIPPED, false, "NONE", "TMAP 교통",
                    "지금 위치를 몰라 길 상황은 보지 않았습니다.", Map.of()), null};
        }
        if (!tmap.ready()) {
            return new Object[]{new Finding(Kind.TRAFFIC, Status.UNAVAILABLE, false, "TMAP_LIVE", "TMAP 실시간 교통",
                    "교통 정보는 아직 연동 전입니다.", Map.of()), null};
        }
        Integer sec = null;
        try {
            JsonNode res = tmap.carRoute(fromLng, fromLat, toLng, toLat, "지금 위치", String.valueOf(next.get("name")), null);
            sec = totalSeconds(res);
        } catch (Exception e) {
            log.warn("[장소 확인] TMAP 실패: {}", e.getMessage());
        }
        if (sec == null) {
            return new Object[]{new Finding(Kind.TRAFFIC, Status.UNAVAILABLE, false, "TMAP_LIVE", "TMAP 실시간 교통",
                    "길 상황을 받아 오지 못했습니다.", Map.of()), null};
        }
        int minutes = Math.max(1, Math.round(sec / 60f));
        LocalTime eta = now.toLocalTime().plusMinutes(minutes);
        LocalTime planned = parseTime(String.valueOf(next.getOrDefault("time", "")));
        Integer late = null;
        boolean issue = false;
        String summary = "지금 출발하면 약 " + minutes + "분, " + eta.format(DateTimeFormatter.ofPattern("HH:mm")) + "쯤 도착합니다";
        if (planned != null && !now.toLocalTime().isAfter(planned.plusHours(3))) {
            long diff = java.time.Duration.between(planned, eta).toMinutes();
            if (diff >= LATE_MIN) {
                late = (int) diff;
                issue = true;
                summary += "(계획 " + planned.format(DateTimeFormatter.ofPattern("HH:mm")) + "보다 " + diff + "분 늦음)";
            } else {
                summary += "(계획 " + planned.format(DateTimeFormatter.ofPattern("HH:mm")) + ")";
            }
        }
        return new Object[]{new Finding(Kind.TRAFFIC, Status.OK, issue, "TMAP_LIVE", "TMAP 실시간 교통", summary + ".",
                PlaceCheck.values("minutes", minutes, "eta", eta.toString(), "lateMin", late)), late};
    }

    static Integer totalSeconds(JsonNode res) {
        if (res == null) return null;
        for (JsonNode f : res.path("features")) {
            JsonNode t = f.path("properties").path("totalTime");
            if (t.isNumber() || (t.isTextual() && !t.asText().isBlank())) {
                try { return Integer.parseInt(t.asText()); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }

    private static LocalTime parseTime(String s) {
        var m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s == null ? "" : s);
        if (!m.find()) return null;
        try { return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))); }
        catch (Exception e) { return null; }
    }

    private static Double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
