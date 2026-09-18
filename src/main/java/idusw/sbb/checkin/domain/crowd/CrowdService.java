package idusw.sbb.checkin.domain.crowd;

import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.tour.dto.ConcentrationRate;
import idusw.sbb.checkin.domain.tour.service.ConcentrationService;
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
 * 날짜별 혼잡도 예측 — 관광공사 집중률을 다섯 단계로 읽는다.
 *
 * 집중률 API 는 한 번 부르면 그 시군구 관광지 × 30일을 배열로 준다.
 * 장소 하나 물을 때마다 다시 부르면 같은 응답을 여러 번 받는다.
 * 지역·날짜 한 번 불러 놓고 장소들을 그 안에서 찾는다.
 *
 * 장소 이름은 서로 다르게 적혀 온다 — 「해운대해수욕장」 「해운대 해수욕장」
 * 「해운대해변」. 공백을 지우고 서로 품는지 본다.
 *
 * 응답을 저장하지 않는다. 캐시는 분 단위 메모리뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrowdService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long TTL_MS = 10 * 60_000;     // 10분
    private static final int  ROWS = 600;               // 관광지 × 30일이 한 번에 온다

    private final ConcentrationService concentrationService;

    private record Cached(List<ConcentrationRate> rates, long at) {}
    private final Map<String, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 지역 하나의 예측 전체 (시군구 단위). */
    public List<ConcentrationRate> rates(String areaCd, String signguCd) {
        String key = areaCd + ":" + signguCd;
        Cached c = cache.get(key);
        long now = System.currentTimeMillis();
        if (c != null && now - c.at() < TTL_MS) return c.rates();
        try {
            List<ConcentrationRate> got = concentrationService.predict(areaCd, signguCd, ROWS, 1);
            cache.put(key, new Cached(got, now));
            return got;
        } catch (Exception e) {
            log.warn("[crowd] 집중률 조회 실패 {} : {}", key, e.getMessage());
            return c != null ? c.rates() : List.of();
        }
    }

    /** 장소 하나, 날짜 하나. */
    public CrowdForecast forecast(String areaCd, String signguCd, String placeName, LocalDate date) {
        List<CrowdForecast> one = forecast(areaCd, signguCd, List.of(placeName), date);
        return one.isEmpty()
                ? CrowdForecast.unknown(placeName, date == null ? null : date.format(YMD), "예측이 없습니다")
                : one.get(0);
    }

    /** 장소 여러 곳, 날짜 하나. 화면은 보통 하루 동선을 통째로 묻는다. */
    public List<CrowdForecast> forecast(String areaCd, String signguCd,
                                        List<String> placeNames, LocalDate date) {
        String want = date != null ? date.format(YMD) : null;
        List<ConcentrationRate> all = rates(areaCd, signguCd);

        List<CrowdForecast> out = new ArrayList<>();
        for (String name : placeNames == null ? List.<String>of() : placeNames) {
            ConcentrationRate hit = bestMatch(all, name, want);
            if (hit == null) {
                out.add(CrowdForecast.unknown(name, want,
                        all.isEmpty() ? "집중률을 받지 못했습니다" : "이 장소는 예측 대상이 아닙니다"));
                continue;
            }
            CrowdLevel lv = CrowdLevel.of(hit.rate());
            out.add(new CrowdForecast(name, hit.date(), hit.rate(),
                    lv == null ? null : lv.key(), lv == null ? null : lv.label(),
                    "TOUR", hit.areaName(), hit.sigunguName(), null));
        }
        return out;
    }

    /** 한 장소의 날짜별 흐름 — 「언제 가면 한적한가」를 화면이 그릴 수 있게. */
    public List<CrowdForecast> timeline(String areaCd, String signguCd, String placeName) {
        List<ConcentrationRate> all = rates(areaCd, signguCd);
        String norm = norm(placeName);

        Map<String, ConcentrationRate> byDate = new LinkedHashMap<>();
        for (ConcentrationRate r : all) {
            if (!matches(norm(r.placeName()), norm)) continue;
            ConcentrationRate had = byDate.get(r.date());
            if (had == null || r.rate() > had.rate()) byDate.put(r.date(), r);
        }

        List<CrowdForecast> out = new ArrayList<>();
        byDate.values().stream()
                .sorted(java.util.Comparator.comparing(ConcentrationRate::date))
                .forEach(r -> {
                    CrowdLevel lv = CrowdLevel.of(r.rate());
                    out.add(new CrowdForecast(r.placeName(), r.date(), r.rate(),
                            lv == null ? null : lv.key(), lv == null ? null : lv.label(),
                            "TOUR", r.areaName(), r.sigunguName(), null));
                });
        return out;
    }

    /**
     * 그 지역에서 그 날 가장 한적한 곳들. 「다른 곳으로」가 쓸 목록이다.
     * @param limit 몇 곳까지
     */
    public List<CrowdForecast> quietest(String areaCd, String signguCd, LocalDate date, int limit) {
        String want = date != null ? date.format(YMD) : null;
        List<CrowdForecast> out = new ArrayList<>();

        /* 같은 장소가 날짜별로 여러 줄 오므로 그 날짜만 고르고 장소로 접는다 */
        Map<String, ConcentrationRate> byPlace = new LinkedHashMap<>();
        for (ConcentrationRate r : rates(areaCd, signguCd)) {
            if (want != null && !want.equals(r.date())) continue;
            byPlace.putIfAbsent(r.placeName(), r);
        }

        byPlace.values().stream()
                .sorted(java.util.Comparator.comparingDouble(ConcentrationRate::rate))
                .limit(Math.max(1, Math.min(50, limit)))
                .forEach(r -> {
                    CrowdLevel lv = CrowdLevel.of(r.rate());
                    out.add(new CrowdForecast(r.placeName(), r.date(), r.rate(),
                            lv == null ? null : lv.key(), lv == null ? null : lv.label(),
                            "TOUR", r.areaName(), r.sigunguName(), null));
                });
        return out;
    }

    /* ── 이름 맞추기 ───────────────────────────────────────── */

    private ConcentrationRate bestMatch(List<ConcentrationRate> all, String name, String wantDate) {
        String norm = norm(name);
        if (norm.isEmpty()) return null;
        ConcentrationRate best = null;
        for (ConcentrationRate r : all) {
            if (wantDate != null && !wantDate.equals(r.date())) continue;
            if (!matches(norm(r.placeName()), norm)) continue;
            /* 같은 이름이 여러 줄이면 붐비는 쪽을 택한다.
               덜 붐비는 값을 골라 「한적하다」고 말하면 가서 줄을 선다. */
            if (best == null || r.rate() > best.rate()) best = r;
        }
        return best;
    }

    /** 공백·괄호를 지우고 비교한다. 「해운대 해수욕장」과 「해운대해수욕장」은 같은 곳이다. */
    public static String norm(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s()\\[\\]·,.-]", "");
    }

    public static boolean matches(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    public static void main(String[] args) {
        assert matches(norm("해운대해수욕장"), norm("해운대 해수욕장")) : "공백만 다르면 같다";
        assert matches(norm("SEA LIFE 부산아쿠아리움"), norm("부산아쿠아리움")) : "품으면 같다";
        assert !matches(norm("동백섬"), norm("광안리")) : "다른 곳";
        assert !matches(norm(""), norm("미포")) : "빈 이름은 안 맞는다";
        System.out.println("OK 장소 이름 맞추기");
    }
}
