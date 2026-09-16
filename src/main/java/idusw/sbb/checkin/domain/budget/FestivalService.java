package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.budget.dto.Festival;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여행 기간에 그 지역에서 열리는 축제.
 *
 * searchFestival2 의 파라미터 뜻이 헷갈린다 —
 *   eventStartDate  「이 날 이후에 시작하는」 행사
 *   eventEndDate    「이 날 이전에 끝나는」 행사
 * 둘을 여행 기간으로 주면 기간 안에 완전히 들어가는 축제만 나온다.
 * 이미 시작해서 여행 기간까지 이어지는 축제가 빠진다 — 실제로 그런 축제가
 * 더 많다. 그래서 시작일을 넉넉히 앞으로 잡고 겹치는지는 여기서 본다.
 *
 * 확인한 것 (2026-09-17) — 2026년 부산 축제는 아직 등록이 거의 없다.
 * eventStartDate=20260901&areaCode=6 은 0건, 2025년 같은 질의는 나온다.
 * 축제 등록은 그 해가 가까워지면서 채워진다. 없으면 없다고 말한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalService {

    private static final String SERVICE = "KorService2";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int LOOKBACK_DAYS = 90;    // 이미 시작한 축제를 놓치지 않으려고
    private static final long TTL_MS = 30 * 60_000;

    private final TourApiClient client;

    private record Cached(List<Festival> list, long at) {}
    private final Map<String, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @param tourAreaCode KorService2 지역코드 (AreaCode.tourAreaCode 로 구한다)
     */
    public List<Festival> during(String tourAreaCode, LocalDate from, LocalDate to) {
        if (from == null) return List.of();
        LocalDate end = to == null || to.isBefore(from) ? from : to;

        String key = tourAreaCode + ":" + from.format(YMD);
        Cached c = cache.get(key);
        long now = System.currentTimeMillis();
        List<Festival> all = (c != null && now - c.at() < TTL_MS) ? c.list() : null;

        if (all == null) {
            all = fetch(tourAreaCode, from.minusDays(LOOKBACK_DAYS));
            cache.put(key, new Cached(all, now));
        }

        String f = from.format(YMD), t = end.format(YMD);
        List<Festival> out = new ArrayList<>();
        for (Festival x : all) {
            /* 겹치는가 — 축제 시작 <= 여행 끝  &&  축제 끝 >= 여행 시작 */
            if (x.startDate() == null || x.endDate() == null) continue;
            if (x.startDate().compareTo(t) <= 0 && x.endDate().compareTo(f) >= 0) out.add(x);
        }
        return out;
    }

    private List<Festival> fetch(String tourAreaCode, LocalDate since) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", "100");
        p.put("pageNo", "1");
        p.put("arrange", "A");
        p.put("eventStartDate", since.format(YMD));
        if (tourAreaCode != null && !tourAreaCode.isBlank()) p.put("areaCode", tourAreaCode);

        JsonNode items;
        try {
            items = client.items(SERVICE, "searchFestival2", p);
        } catch (Exception e) {
            log.warn("[festival] 조회 실패: {}", e.getMessage());
            return List.of();
        }

        List<Festival> out = new ArrayList<>();
        if (items.isObject()) out.add(toFestival(items));
        else for (JsonNode n : items) out.add(toFestival(n));
        return out;
    }

    private Festival toFestival(JsonNode n) {
        return new Festival(
                text(n, "contentid"),
                text(n, "title"),
                text(n, "eventstartdate"),
                text(n, "eventenddate"),
                (text(n, "addr1") + " " + text(n, "addr2")).trim(),
                text(n, "firstimage"),
                num(n, "mapy"),
                num(n, "mapx"));
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    private static Double num(JsonNode n, String f) {
        String s = text(n, f);
        if (s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }
}
