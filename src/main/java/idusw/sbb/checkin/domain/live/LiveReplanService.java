package idusw.sbb.checkin.domain.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.route.service.AiRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실시간 화면의 「순서 바꾸기」가 실제로 하는 일.
 *
 * <p>붐비는 곳을 남은 구간의 뒤로 미룬다. 판정 규칙은 {@link LiveReplan} 에 있고
 * 여기서는 동선 JSON 을 다시 세워 저장하는 일만 한다.
 *
 * <p>지나간 곳은 건드리지 않는다. 이미 다녀온 일정이 바뀌면 사용자는
 * 자기가 뭘 했는지 알 수 없게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveReplanService {

    private final TravelPlanRepository planRepository;
    private final AiRouteService aiRouteService;
    private final ObjectMapper objectMapper;

    /**
     * @param tripId 여행
     * @param dayNo  며칠째 (1부터)
     * @param userId 부른 사람. 남의 여행을 고칠 수 없다
     * @return 무엇이 바뀌었는지. 바꿀 게 없으면 changed=false
     */
    public Map<String, Object> swapCrowded(Long tripId, int dayNo, Long userId) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행을 찾지 못했습니다."));

        /* 남의 여행을 고칠 수 없다. 읽기는 공유 링크로 열려 있지만 쓰기는 본인 것만이다 */
        if (userId == null || plan.getUser() == null || !userId.equals(plan.getUser().getId())) {
            throw new SecurityException("이 여행을 고칠 권한이 없습니다.");
        }

        String json = plan.getRouteJson() != null && !plan.getRouteJson().isBlank()
                ? plan.getRouteJson() : plan.getDraftRouteJson();
        if (json == null || json.isBlank()) {
            return result(false, "이 여행에는 아직 경로가 없습니다.", List.of());
        }

        JsonNode route;
        try {
            route = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[실시간] 동선 JSON 을 읽지 못했습니다 trip={}: {}", tripId, e.getMessage());
            return result(false, "동선을 읽지 못했습니다.", List.of());
        }
        if (!route.isArray() || dayNo < 1 || dayNo > route.size()) {
            return result(false, "그 일차가 없습니다.", List.of());
        }

        JsonNode day = route.get(dayNo - 1);
        if (!(day.path("places") instanceof ArrayNode places)) {
            return result(false, "그날 들를 곳이 없습니다.", List.of());
        }

        /* 구간(transit) 노드는 빼고 정거장만 다시 세운다.
           순서가 바뀌면 「해운대 -> 남포동 35분」이 더는 맞지 않는다. 저장할 때 다시 채워진다. */
        List<Integer> stopPositions = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            JsonNode p = places.get(i);
            if (p.hasNonNull("transit")) continue;
            if (p.path("name").asText("").isBlank()) continue;
            stopPositions.add(i);
        }
        if (stopPositions.size() < 2) {
            return result(false, "바꿀 만큼 일정이 많지 않습니다.", List.of());
        }

        Integer[] crowds = new Integer[stopPositions.size()];
        String[] times = new String[stopPositions.size()];
        for (int i = 0; i < stopPositions.size(); i++) {
            JsonNode p = places.get(stopPositions.get(i));
            crowds[i] = p.hasNonNull("crowd") ? p.path("crowd").asInt() : null;
            times[i] = p.path("time").asText("");
        }

        /* 오늘 일정이면 시각이 지난 곳은 손대지 않는다 */
        LocalTime now = isToday(plan, dayNo) ? LocalTime.now() : null;
        int from = LiveReplan.firstRemaining(times, now);

        int[] order = LiveReplan.order(crowds, from, LiveReplan.BUSY);
        if (!LiveReplan.changed(order)) {
            return result(false, "지금 순서가 이미 한적한 순입니다.", List.of());
        }

        List<String> moved = new ArrayList<>();
        for (int i = from; i < order.length; i++) {
            if (order[i] != i) {
                moved.add(places.get(stopPositions.get(order[i])).path("name").asText(""));
            }
        }

        ArrayNode rebuilt = LiveReplan.rebuild(places, order, stopPositions);
        ((ObjectNode) day).set("places", rebuilt);

        aiRouteService.saveAiRouteToDb(tripId, route.toString());
        log.info("[실시간] 붐비는 곳을 뒤로 미뤘습니다 trip={} day={} moved={}", tripId, dayNo, moved);

        return result(true, "붐비는 곳을 뒤로 미뤘습니다.", moved);
    }

    private boolean isToday(TravelPlan plan, int dayNo) {
        if (plan.getStartDate() == null) return false;
        return plan.getStartDate().plusDays(dayNo - 1L).equals(LocalDate.now());
    }

    private Map<String, Object> result(boolean changed, String note, List<String> moved) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("changed", changed);
        m.put("note", note);
        m.put("moved", moved);
        return m;
    }
}
