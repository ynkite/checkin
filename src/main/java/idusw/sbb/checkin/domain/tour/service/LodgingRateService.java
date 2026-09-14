package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.LodgingRate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 숙박 1박 요금 추정 (D 확장) — 홍은표 예산 엔진 입력.
 *
 * - 관광공사 detailInfo2 에 성수기/비수기 × 주중/주말 요금이 있으면 그대로 확정.
 * - 값이 0(대형 호텔 등)이면 같은 지역 같은 계절의 평균으로 추정 ("지역 평균 N=k").
 * - 여행 날짜로 성수기(7~8월)·주말(금·토 체크인)을 판정해 맞는 요금을 고른다.
 *
 * 지역 평균은 API 를 여러 번 호출하므로 결과만 분 단위 메모리 캐시(영구저장 금지 규정 준수).
 */
@Service
@RequiredArgsConstructor
public class LodgingRateService {

    private static final String SERVICE = "KorService2";
    private static final int SAMPLE = 12;               // 지역 평균 표본 상한
    private static final long CACHE_TTL_MS = 10 * 60_000; // 10분

    private final TourApiClient client;

    private record Avg(long value, int n) {}
    private record Cached(Avg avg, long at) {}
    private final Map<String, Cached> regionCache = new ConcurrentHashMap<>();

    /**
     * 특정 숙소의 여행 날짜 1박 요금.
     * @param contentId 숙소 contentId
     * @param areaCode  지역코드 (추정 시 지역 평균 계산에 사용)
     * @param checkIn   체크인 날짜 (성수기·주말 판정)
     */
    public LodgingRate nightly(String contentId, String areaCode, LocalDate checkIn) {
        boolean peak = isPeak(checkIn);
        boolean weekend = isWeekend(checkIn);
        String field = feeField(peak, weekend);
        String label = seasonLabel(peak, weekend);

        long confirmed = minFee(rooms(contentId), field);
        if (confirmed > 0) return LodgingRate.confirmed(confirmed, label);

        Avg avg = regionalAverage(areaCode, field);
        if (avg.n() > 0) return LodgingRate.estimated(avg.value(), avg.n(), label);

        return LodgingRate.none(label);
    }

    // ── 계절·요일 판정 ──

    static boolean isPeak(LocalDate d) {
        int m = d.getMonthValue();
        return m == 7 || m == 8;                        // 여름 성수기
    }

    static boolean isWeekend(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.FRIDAY || w == DayOfWeek.SATURDAY; // 체크인 기준 주말
    }

    static String feeField(boolean peak, boolean weekend) {
        if (peak)    return weekend ? "roompeakseasonminfee2" : "roompeakseasonminfee1";
        return             weekend ? "roomoffseasonminfee2"  : "roomoffseasonminfee1";
    }

    static String seasonLabel(boolean peak, boolean weekend) {
        return (peak ? "성수기 " : "비수기 ") + (weekend ? "주말" : "주중");
    }

    // ── 요금 조회 ──

    /** 숙소의 객실별 반복정보(detailInfo2). */
    private JsonNode rooms(String contentId) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("contentId", contentId);
        p.put("contentTypeId", "32");
        p.put("numOfRows", "30");
        return client.items(SERVICE, "detailInfo2", p);
    }

    /** 객실들 중 해당 요금 필드의 0 초과 최솟값 (가장 싼 방 기준). 없으면 0. */
    private long minFee(JsonNode rooms, String field) {
        long min = 0;
        for (JsonNode r : arr(rooms)) {
            long v = parseLong(text(r, field));
            if (v > 0 && (min == 0 || v < min)) min = v;
        }
        return min;
    }

    /** 지역 평균 — 같은 areaCode 숙소 표본에서 해당 필드 0 초과 값들의 평균. */
    private Avg regionalAverage(String areaCode, String field) {
        String key = areaCode + "|" + field;
        Cached c = regionCache.get(key);
        if (c != null && System.currentTimeMillis() - c.at() < CACHE_TTL_MS) return c.avg();

        List<Long> fees = new ArrayList<>();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", String.valueOf(SAMPLE));
        p.put("pageNo", "1");
        p.put("arrange", "A");
        p.put("contentTypeId", "32");
        if (areaCode != null) p.put("areaCode", areaCode);

        for (JsonNode spot : arr(client.items(SERVICE, "areaBasedList2", p))) {
            String cid = text(spot, "contentid");
            if (cid.isBlank()) continue;
            long fee = minFee(rooms(cid), field);
            if (fee > 0) fees.add(fee);
        }

        Avg avg = fees.isEmpty()
                ? new Avg(0, 0)
                : new Avg(Math.round(fees.stream().mapToLong(Long::longValue).average().orElse(0)), fees.size());
        regionCache.put(key, new Cached(avg, System.currentTimeMillis()));
        return avg;
    }

    // ── helpers ──

    private static Iterable<JsonNode> arr(JsonNode node) {
        List<JsonNode> list = new ArrayList<>();
        if (node == null) return list;
        if (node.isObject()) list.add(node);
        else node.forEach(list::add);
        return list;
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isMissingNode() ? "" : v.asText();
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (Exception e) { return 0; }
    }
}
