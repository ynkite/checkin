package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.route.engine.Anchor;
import idusw.sbb.checkin.domain.route.engine.Candidate;
import idusw.sbb.checkin.domain.route.engine.CandidateCategory;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카카오 후보 {@code ObjectNode}(name/lat/lng/sub/stars) 를 엔진의 {@link Candidate}·{@link Anchor} 로
 * 옮기는 경계 어댑터 (설계 결정 10-3·10-4).
 *
 * <p>최종 JSON 에는 {@code stars}·{@code sub} 가 들어가는데 {@link Candidate} 에는 그런 필드가 없다.
 * 엔진을 프레젠테이션 관심사로 오염시키지 않기 위해, 여기서 {@code Candidate.id → 원본 ObjectNode}
 * 역참조 맵을 들고 있다가 {@link RouteJsonWriter} 가 꺼내 쓰게 한다 (결정 10-4).
 *
 * <p>상태(역참조 맵)를 들고 있으므로 <b>스프링 빈이 아니라 요청마다 새로 만들어 쓴다.</b>
 */
public final class CandidateAdapter {

    /** 카카오 후보 맵의 키. 순회 순서를 고정해야 id 가 결정론적으로 붙는다. */
    private static final String[] CANDIDATE_TYPES = {"food", "cafe", "tour"};
    static final String ANCHOR_ID = "stay-000";

    private final Map<String, ObjectNode> originById = new LinkedHashMap<>();

    /**
     * {@code {stay:[…], food:[…], cafe:[…], tour:[…]}} 에서 food/cafe/tour 만 후보로 바꾼다.
     * 숙소는 후보가 아니라 앵커라 여기서 빠진다 (결정 2) — {@link #toAnchor(ObjectNode)} 로 따로 받는다.
     *
     * <p>이름이 비었거나 좌표가 없는 노드는 건너뛴다. 카카오 응답이 들어오는 경계라 방어가 필요하고,
     * 좌표 없는 후보는 어차피 거리 계산에 못 들어간다.
     */
    public List<Candidate> toCandidates(Map<String, List<ObjectNode>> nodesByType) {
        if (nodesByType == null) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (String type : CANDIDATE_TYPES) {
            List<ObjectNode> nodes = nodesByType.getOrDefault(type, Collections.emptyList());
            int sequence = 0;
            for (ObjectNode node : nodes) {
                Candidate candidate = toCandidate(type, node, sequence);
                if (candidate == null) {
                    continue;
                }
                candidates.add(candidate);
                sequence++;
            }
        }
        return candidates;
    }

    /** 숙소 노드 → 앵커. 변환할 수 없으면 null (당일치기이거나 카카오가 숙소를 못 잡은 경우). */
    public Anchor toAnchor(ObjectNode stayNode) {
        String name = textOf(stayNode, "name");
        GeoPoint location = locationOf(stayNode);
        if (name == null || location == null) {
            return null;
        }
        originById.put(ANCHOR_ID, stayNode);
        return new Anchor(ANCHOR_ID, name, location, null, null);
    }

    /** 원본 노드 되찾기 — {@code sub}·{@code stars} 처럼 엔진이 모르는 값을 꺼낼 때 쓴다. */
    public ObjectNode originOf(String candidateId) {
        return originById.get(candidateId);
    }

    /** 최종 JSON 의 {@code type} 값. 엔진 카테고리에는 "stay" 가 없다 — 숙소는 앵커다. */
    public static String jsonType(CandidateCategory category) {
        return switch (category) {
            case FOOD -> "food";
            case CAFE -> "cafe";
            case TOUR -> "tour";
        };
    }

    private Candidate toCandidate(String type, ObjectNode node, int sequence) {
        String name = textOf(node, "name");
        GeoPoint location = locationOf(node);
        if (name == null || location == null) {
            return null;
        }
        // 0 패딩은 사전순 = 입력순을 맞추려는 것이다. SlotOptimizer 의 동점 처리가 id 사전순이라
        // (결정 8) 패딩이 없으면 후보가 10개를 넘는 순간 동점 순서가 뒤바뀐다.
        String id = String.format("%s-%03d", type, sequence);
        originById.put(id, node);
        return new Candidate(id, name, location, categoryOf(type), null, null, null, null);
    }

    private static CandidateCategory categoryOf(String type) {
        return switch (type) {
            case "food" -> CandidateCategory.FOOD;
            case "cafe" -> CandidateCategory.CAFE;
            case "tour" -> CandidateCategory.TOUR;
            default -> throw new IllegalArgumentException("unsupported candidate type: " + type);
        };
    }

    private static String textOf(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static GeoPoint locationOf(ObjectNode node) {
        if (node == null || !node.path("lat").isNumber() || !node.path("lng").isNumber()) {
            return null;
        }
        try {
            return new GeoPoint(node.path("lat").asDouble(), node.path("lng").asDouble());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
