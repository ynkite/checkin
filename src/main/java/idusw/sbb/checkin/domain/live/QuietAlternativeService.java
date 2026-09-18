package idusw.sbb.checkin.domain.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.crowd.CrowdService;
import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.tour.dto.Spot;
import idusw.sbb.checkin.domain.tour.service.KorTourService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 실시간 화면의 「다른 곳으로」가 내밀 후보를 고른다.
 *
 * <p>순서를 바꾸는 것으로 안 될 때가 있다. 하루 종일 붐비는 곳이면 뒤로 미뤄도
 * 소용없다. 그때는 갈 곳 자체를 바꿔야 한다.
 *
 * <p>고르는 규칙 —
 * <ul>
 *   <li>같은 시군구에서 그날 한적한 곳을 받는다 (관광공사 집중률 예측)</li>
 *   <li>지금 가려던 곳보다 <b>뚜렷하게</b> 한적한 곳만 (기본 15 이상 차이).
 *       2~3 차이로 「여기가 더 낫다」고 하면 헛걸음이 된다</li>
 *   <li>그날 일정에 이미 있는 곳은 뺀다</li>
 *   <li><b>좌표를 못 찾으면 버린다.</b> 이름만 있는 곳을 일정에 넣으면
 *       지도에 찍히지 않고 이동시간도 못 잰다</li>
 * </ul>
 *
 * <p>고르기만 하고 바꾸지는 않는다. 무엇으로 바꿀지는 사용자가 정한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuietAlternativeService {

    private final TravelPlanRepository planRepository;
    private final CrowdService crowdService;
    private final KorTourService korTourService;
    private final ObjectMapper objectMapper;

    /** 이만큼은 한적해야 권한다. 차이가 작으면 옮길 값어치가 없다 */
    public static final int MEANINGFUL_GAP = 15;

    /**
     * @param tripId 여행
     * @param dayNo  며칠째 (1부터)
     * @param name   바꾸려는 장소 이름. 비우면 그날 가장 붐비는 곳
     * @param limit  몇 곳까지
     */
    public Map<String, Object> alternatives(Long tripId, int dayNo, String name, int limit) {
        TravelPlan plan = planRepository.findById(tripId).orElse(null);
        if (plan == null) return empty("여행을 찾지 못했습니다.");

        List<Map<String, Object>> stops = stopsOf(plan, dayNo);
        if (stops.isEmpty()) return empty("그날 들를 곳이 없습니다.");

        Map<String, Object> target = pickTarget(stops, name);
        if (target == null) return empty("바꿀 곳을 찾지 못했습니다.");

        Integer targetRate = target.get("crowd") instanceof Number n ? n.intValue() : null;
        String targetName = String.valueOf(target.get("name"));

        AreaCode.Area area = AreaCode.find(plan.getDestination());
        if (area == null || !AreaCode.hasCrowdData(area)) {
            /* 광주·전남처럼 집중률이 없는 지역이 있다. 없는 것을 0(한적)으로 두지 않는다 */
            return empty("이 지역은 집중률 예측이 제공되지 않아 한적한 곳을 고를 수 없습니다.");
        }

        LocalDate date = dateOf(plan, dayNo);
        List<CrowdForecast> quiet;
        try {
            quiet = crowdService.quietest(area.areaCd(), area.signguCd(), date, 30);
        } catch (Exception e) {
            log.warn("[실시간] 한적한 곳 조회 실패 trip={}: {}", tripId, e.getMessage());
            return empty("한적한 곳을 불러오지 못했습니다.");
        }

        Set<String> already = new LinkedHashSet<>();
        for (Map<String, Object> s : stops) {
            already.add(CrowdService.norm(String.valueOf(s.get("name"))));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (CrowdForecast f : quiet) {
            if (out.size() >= Math.max(1, Math.min(10, limit))) break;
            if (f.rate() == null) continue;

            int rate = (int) Math.round(f.rate());
            if (already.contains(CrowdService.norm(f.placeName()))) continue;
            if (!worthMoving(targetRate, rate)) continue;

            Spot spot = findSpot(f.placeName());
            if (spot == null) continue;          /* 좌표가 없으면 일정에 넣을 수 없다 */

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.placeName());
            m.put("lat", spot.lat());
            m.put("lng", spot.lon());
            if (spot.address() != null) m.put("address", spot.address());
            m.put("crowd", rate);
            m.put("crowdLabel", f.levelLabel());
            m.put("crowdKey", f.levelKey());
            m.put("note", targetName + " " + targetRate + ", " + f.placeName() + " " + rate);
            out.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("target", targetName);
        if (targetRate != null) res.put("targetCrowd", targetRate);
        res.put("date", String.valueOf(date));
        res.put("source", "TOUR");
        res.put("candidates", out);
        res.put("note", out.isEmpty()
                ? "지금보다 뚜렷하게 한적한 곳을 찾지 못했습니다."
                : "한국관광공사 집중률 예측 기준입니다.");
        return res;
    }

    /**
     * 옮길 값어치가 있는가.
     *
     * <p>지금 곳의 집중률을 모르면 권하지 않는다 — 모르는 값과 비교해서
     * 「여기가 더 한적하다」고 말할 수 없다.
     */
    static boolean worthMoving(Integer targetRate, int candidateRate) {
        if (targetRate == null) return false;
        return targetRate - candidateRate >= MEANINGFUL_GAP;
    }

    /* 이름을 주면 그 곳, 안 주면 그날 가장 붐비는 곳 */
    private Map<String, Object> pickTarget(List<Map<String, Object>> stops, String name) {
        if (name != null && !name.isBlank()) {
            String want = CrowdService.norm(name);
            for (Map<String, Object> s : stops) {
                if (CrowdService.matches(CrowdService.norm(String.valueOf(s.get("name"))), want)) {
                    return s;
                }
            }
            return null;
        }
        Map<String, Object> worst = null;
        int worstRate = -1;
        for (Map<String, Object> s : stops) {
            if (s.get("crowd") instanceof Number n && n.intValue() > worstRate) {
                worstRate = n.intValue();
                worst = s;
            }
        }
        return worst != null ? worst : stops.get(0);
    }

    private Spot findSpot(String placeName) {
        try {
            List<Spot> found = korTourService.searchByName(placeName, null, 3);
            for (Spot s : found) {
                if (s.lat() != 0 && s.lon() != 0) return s;
            }
        } catch (Exception e) {
            log.warn("[실시간] 좌표 조회 실패 {}: {}", placeName, e.getMessage());
        }
        return null;
    }

    private LocalDate dateOf(TravelPlan plan, int dayNo) {
        return plan.getStartDate() != null
                ? plan.getStartDate().plusDays(dayNo - 1L) : LocalDate.now();
    }

    private List<Map<String, Object>> stopsOf(TravelPlan plan, int dayNo) {
        String json = plan.getRouteJson() != null && !plan.getRouteJson().isBlank()
                ? plan.getRouteJson() : plan.getDraftRouteJson();
        if (json == null || json.isBlank()) return List.of();

        try {
            JsonNode route = objectMapper.readTree(json);
            if (!route.isArray() || dayNo < 1 || dayNo > route.size()) return List.of();

            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode p : route.get(dayNo - 1).path("places")) {
                if (p.hasNonNull("transit")) continue;
                String nm = p.path("name").asText("");
                if (nm.isBlank()) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", nm);
                if (p.hasNonNull("crowd")) m.put("crowd", p.path("crowd").asInt());
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.warn("[실시간] 동선 JSON 을 읽지 못했습니다 trip={}: {}", plan.getId(), e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> empty(String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("candidates", List.of());
        m.put("note", note);
        return m;
    }
}
