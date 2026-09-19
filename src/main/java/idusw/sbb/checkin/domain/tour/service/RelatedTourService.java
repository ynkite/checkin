package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.RelatedSpot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TarRlteTarService1 관광지별 연관 관광지 — 대안 후보 선별 (홍성찬 동선 엔진).
 * rlteCtgryLclsNm(관광지/음식/숙박) 로 유형이 나뉘고 rlteRank 로 순위가 온다.
 */
@Service
@RequiredArgsConstructor
public class RelatedTourService {

    private static final String SERVICE = "TarRlteTarService1";

    private final TourApiClient client;

    /**
     * 특정 시군구의 연관 관광지 목록 (areaBasedList1).
     *
     * @param baseYm    기준 연월 YYYYMM (필수)
     * @param areaCd    지역코드 (필수)
     * @param signguCd  시군구코드 (필수)
     */
    public List<RelatedSpot> relatedList(String baseYm, String areaCd, String signguCd, int numOfRows, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("baseYm", baseYm);
        params.put("areaCd", areaCd);
        params.put("signguCd", signguCd);

        JsonNode items = client.items(SERVICE, "areaBasedList1", params);
        List<RelatedSpot> result = new ArrayList<>();
        if (items.isObject()) {
            result.add(toRelated(items));
        } else {
            for (JsonNode item : items) result.add(toRelated(item));
        }
        return result;
    }

    /* ── 챗봇이 쓰는 추천 ─────────────────────────────────────────── */

    /** OK 는 추천이 있다. NO_AREA 는 지역코드를 모르는 여행지, UNAVAILABLE 은 불렀는데 못 받은 것. */
    public enum Status { OK, NO_AREA, UNAVAILABLE }

    /**
     * @param basedOn 추천의 기준이 된 장소 이름. 일정의 장소와 맞는 것이 없어 지역 전체에서 뽑았으면 null
     */
    public record Suggestion(Status status, String areaName, String baseYm, String basedOn, List<RelatedSpot> spots) {
        static Suggestion of(Status s, String area) { return new Suggestion(s, area, null, null, List.of()); }
    }

    /* 시군구 한 곳의 목록은 한 달 단위 통계라 자주 바뀌지 않는다. 대화마다 2천 건을 다시 받지 않도록 둔다.
       빈 결과는 담지 않는다 — 한 번 실패한 지역이 계속 비어 보이면 안 된다. */
    private final Map<String, List<JsonNode>> areaCache = new java.util.concurrent.ConcurrentHashMap<>();

    public Suggestion suggest(String destination, List<String> anchors, java.util.Collection<String> exclude, int limit) {
        return suggest(destination, anchors, exclude, limit, java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")));
    }

    Suggestion suggest(String destination, List<String> anchors, java.util.Collection<String> exclude,
                       int limit, java.time.YearMonth now) {
        idusw.sbb.checkin.domain.crowd.AreaCode.Area area = idusw.sbb.checkin.domain.crowd.AreaCode.find(destination);
        if (area == null) return Suggestion.of(Status.NO_AREA, null);

        /* 이번 달 통계는 아직 없다. 직전 달부터, 비었으면 그 전 달 */
        String ym = null;
        List<JsonNode> rows = List.of();
        for (int back = 1; back <= 2 && rows.isEmpty(); back++) {
            ym = now.minusMonths(back).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            rows = areaRows(ym, area.areaCd(), area.signguCd());
        }
        if (rows.isEmpty()) return Suggestion.of(Status.UNAVAILABLE, area.fullName());

        java.util.Set<String> skip = new java.util.HashSet<>();
        if (exclude != null) exclude.forEach(n -> skip.add(norm(n)));

        /* 1) 일정에 있는 장소가 기준 관광지이면 그 장소와 함께 간 곳을 순위대로 */
        if (anchors != null) {
            for (String anchor : anchors) {
                String a = norm(anchor);
                if (a.length() < 2) continue;
                List<RelatedSpot> picked = rows.stream()
                        .filter(n -> sameName(norm(text(n, "tAtsNm")), a))
                        .map(this::toRelated)
                        .filter(s -> !"숙박".equals(s.category()))
                        .filter(s -> !skip.contains(norm(s.toName())))
                        .sorted(java.util.Comparator.comparingInt(RelatedSpot::rank))
                        .limit(limit)
                        .toList();
                if (!picked.isEmpty()) return new Suggestion(Status.OK, area.fullName(), ym, anchor, picked);
            }
        }

        /* 2) 맞는 장소가 없으면 이 지역에서 여러 관광지와 함께 가장 자주 묶인 곳 */
        Map<String, Integer> count = new LinkedHashMap<>();
        Map<String, RelatedSpot> first = new LinkedHashMap<>();
        for (JsonNode n : rows) {
            RelatedSpot s = toRelated(n);
            if ("숙박".equals(s.category()) || skip.contains(norm(s.toName()))) continue;
            count.merge(s.toName(), 1, Integer::sum);
            first.putIfAbsent(s.toName(), s);
        }
        List<RelatedSpot> top = count.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> first.get(e.getKey()))
                .toList();
        if (top.isEmpty()) return Suggestion.of(Status.UNAVAILABLE, area.fullName());
        return new Suggestion(Status.OK, area.fullName(), ym, null, top);
    }

    private List<JsonNode> areaRows(String baseYm, String areaCd, String signguCd) {
        String key = signguCd + "|" + baseYm;
        List<JsonNode> cached = areaCache.get(key);
        if (cached != null) return cached;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "3000");
        params.put("pageNo", "1");
        params.put("baseYm", baseYm);
        params.put("areaCd", areaCd);
        params.put("signguCd", signguCd);
        JsonNode items = client.items(SERVICE, "areaBasedList1", params);

        List<JsonNode> rows = new ArrayList<>();
        if (items.isObject()) rows.add(items);
        else items.forEach(rows::add);
        if (!rows.isEmpty()) areaCache.put(key, rows);
        return rows;
    }

    /* 「경포해변」과 「경포해수욕장」처럼 다른 이름은 못 맞춘다. 틀리게 맞추느니 지역 전체로 넘긴다. */
    static boolean sameName(String base, String anchor) {
        if (base.isEmpty() || anchor.isEmpty()) return false;
        if (base.equals(anchor)) return true;
        /* 「경주불국사」↔「불국사」처럼 앞뒤에 지역명 정도만 더 붙은 경우. 「경주역」↔「경주역사유적지구」는 막는다 */
        String longer = base.length() >= anchor.length() ? base : anchor;
        String shorter = longer == base ? anchor : base;
        return shorter.length() >= 3
                && longer.length() - shorter.length() <= 3
                && (longer.startsWith(shorter) || longer.endsWith(shorter));
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("[\\s()·,./\\-]", "");
    }

    private RelatedSpot toRelated(JsonNode n) {
        return new RelatedSpot(
                text(n, "tAtsNm"),
                text(n, "rlteTatsNm"),
                text(n, "rlteCtgryLclsNm"),
                parseInt(text(n, "rlteRank")),
                text(n, "rlteRegnNm"),
                text(n, "rlteSignguNm")
        );
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() ? "" : v.asText();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }
}
