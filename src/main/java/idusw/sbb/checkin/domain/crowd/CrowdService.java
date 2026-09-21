package idusw.sbb.checkin.domain.crowd;

import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.domain.tour.dto.ConcentrationRate;
import idusw.sbb.checkin.domain.tour.service.ConcentrationService;
import idusw.sbb.checkin.domain.tour.service.VisitorTrendService;
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
 * 집중률이 아예 오지 않는 지역(광주·전남, 조회 실패)은 DataLab 시도 외지인 방문자 수로
 * 가늠한다. 같은 요일 평균보다 얼마나 많은지를 단계로 옮긴 추정이라 source 를
 * VISITOR_EST 로 따로 두고 rate 는 비운다. 방문자 자료도 없으면 지금처럼 값 없음이다.
 *
 * 응답을 저장하지 않는다. 캐시는 분 단위 메모리뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrowdService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long TTL_MS = 10 * 60_000;     // 10분
    /* 관광지 × 30일이 한 번에 온다. 600 이면 20곳까지다 — 실호출로 재 보니 부산 해운대구는
       19곳(570행)이라 겨우 들어오고, 대전 중구는 22곳(660행), 서울 종로구는 113곳(3390행)이라
       잘렸다. 잘린 지역은 붐비는 곳이 목록에 없어 「한적하다」로 읽힌다.
       4000 행을 물어도 0.4초 · 426KB 로 한 번에 오고 10분 캐시된다 */
    private static final int  ROWS = 4000;

    private final ConcentrationService concentrationService;
    private final VisitorTrendService visitorTrend;

    private record Cached(List<ConcentrationRate> rates, long at) {}
    private final Map<String, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private record Est(VisitorTrendService.Trend trend, long at) {}
    private final Map<String, Est> estCache = new java.util.concurrent.ConcurrentHashMap<>();

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
        AreaCode.Area area = AreaCode.byCode(signguCd);
        /* 광주·전남은 집중률이 없다고 확인된 곳이라 부르지 않는다 */
        List<ConcentrationRate> all = area != null && !AreaCode.hasCrowdData(area)
                ? List.of() : rates(areaCd, signguCd);

        List<CrowdForecast> out = new ArrayList<>();
        for (String name : placeNames == null ? List.<String>of() : placeNames) {
            ConcentrationRate hit = bestMatch(all, name, want);
            if (hit == null) {
                out.add(all.isEmpty() ? estimate(area, name, want)
                        : CrowdForecast.unknown(name, want, "이 장소는 예측 대상이 아닙니다"));
                continue;
            }
            CrowdLevel lv = CrowdLevel.of(hit.rate());
            out.add(new CrowdForecast(name, hit.date(), hit.rate(),
                    lv == null ? null : lv.key(), lv == null ? null : lv.label(),
                    "TOUR", hit.areaName(), hit.sigunguName(), null));
        }
        return out;
    }

    /**
     * 집중률이 없을 때의 추정. 시도 외지인 방문자가 같은 요일 평균보다 얼마나 많은가를
     * 다섯 단계로 옮긴다 — ±10% 안이면 정상, 30% 넘게 벗어나면 매우 혼잡/한적.
     * 시도 전체·과거 실측이라 장소 하나의 그날 값이 아니다. note 에 그렇게 적는다.
     */
    private CrowdForecast estimate(AreaCode.Area area, String name, String want) {
        VisitorTrendService.Trend t = trend(area);
        if (t == null || t.status() != VisitorTrendService.Status.OK || t.deltaPercent() == null) {
            return CrowdForecast.unknown(name, want, area != null && !AreaCode.hasCrowdData(area)
                    ? CrowdController.topicParticle(area.sido()) + " 관광공사 집중률 예측 대상이 아닙니다"
                    : "집중률을 받지 못했습니다");
        }
        int d = t.deltaPercent();
        CrowdLevel lv = d >= 30 ? CrowdLevel.VERY_HIGH : d >= 10 ? CrowdLevel.HIGH
                : d > -10 ? CrowdLevel.NORMAL : d > -30 ? CrowdLevel.LOW : CrowdLevel.VERY_LOW;
        String note = "추정 — " + t.areaName() + " 외지인 방문자가 " + t.latestDate()
                + " 기준 같은 요일 평균" + (d == 0 ? "과 같습니다" : "보다 " + Math.abs(d) + "% " + (d > 0 ? "많습니다" : "적습니다"));
        return new CrowdForecast(name, want, null, lv.key(), lv.label(),
                "VISITOR_EST", t.areaName(), area.sigungu(), note);
    }

    private VisitorTrendService.Trend trend(AreaCode.Area area) {
        if (area == null) return null;
        Est e = estCache.get(area.areaCd());
        long now = System.currentTimeMillis();
        if (e != null && now - e.at() < TTL_MS) return e.trend();
        try {
            VisitorTrendService.Trend t = visitorTrend.summary(area);
            estCache.put(area.areaCd(), new Est(t, now));
            return t;
        } catch (Exception ex) {
            log.warn("[crowd] 방문자 추정 실패 {} : {}", area.areaCd(), ex.getMessage());
            return e != null ? e.trend() : null;
        }
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

    /**
     * 한쪽이 다른 쪽을 품고 있으면 같은 곳으로 봤는데, 그것만으로는 모자랐다.
     *
     * <p>집중률 목록에 「마린시티」가 있다. 메인 화면의 카페 이름이
     * 「시드니앤솔트 마린시티점」이라, 품고 있다는 이유로 카페에
     * <b>집중률 62.51 「보통」이 붙었다.</b> 집중률 대상이 아닌 곳에
     * 진짜처럼 보이는 숫자가 붙는 것이라 없는 것보다 나쁘다.
     *
     * <p>짧은 쪽이 긴 쪽의 절반쯤은 되어야 같은 곳으로 본다. 실제로 맞춰야 하는 것들 —
     * <pre>
     *   스파랜드센텀   / 스파랜드센텀시티        7/9  = .78  같은 곳이 맞다
     *   부산아쿠아리움  / SEALIFE부산아쿠아리움  7/15 = .47  같은 곳이 맞다
     *   마린시티     / 시드니앤솔트마린시티점    4/11 = .36  다른 곳이다
     * </pre>
     */
    public static boolean matches(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        String longer  = a.length() >= b.length() ? a : b;
        String shorter = a.length() >= b.length() ? b : a;
        if (!longer.contains(shorter)) return false;
        return shorter.length() * 20 >= longer.length() * 9;   /* 45% */
    }

    public static void main(String[] args) {
        assert matches(norm("해운대해수욕장"), norm("해운대 해수욕장")) : "공백만 다르면 같다";
        assert matches(norm("SEA LIFE 부산아쿠아리움"), norm("부산아쿠아리움")) : "품으면 같다";
        assert !matches(norm("동백섬"), norm("광안리")) : "다른 곳";
        assert matches(norm("스파랜드 센텀"), norm("스파랜드 센텀시티")) : "같은 곳";
        assert !matches(norm("마린시티"), norm("시드니앤솔트 마린시티점")) : "품어도 다른 곳";
        assert !matches(norm(""), norm("미포")) : "빈 이름은 안 맞는다";
        System.out.println("OK 장소 이름 맞추기");
    }
}
