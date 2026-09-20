package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import idusw.sbb.checkin.domain.tour.dto.HubSpot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지도 화면 「이 지역 여행 정보」 — 관광공사 거점 관광지 · 반려동물 동반 · 무장애 여행.
 *
 * 지도를 열면 셋을 한 번에 부른다. 칩을 켠 사람만 부르면 심사에서 호출 기록이 0이 될 수 있다.
 *
 * 값이 없을 때 말을 나눈다.
 *   UNAVAILABLE — 불렀는데 못 받았다(확인되지 않음)
 *   NONE        — 받았는데 0건이다(없음)
 *   NO_AREA     — 여행지를 지역 코드로 못 바꿨다
 * 무장애 상세를 못 받은 곳은 「편의시설 없음」이 아니라 「확인되지 않았습니다」다.
 */
@Service
@RequiredArgsConstructor
public class TourAreaInfoService {

    public enum Status { OK, NONE, UNAVAILABLE, NO_AREA }

    public record Hub(String name, String category, int rank, double lat, double lng) {}

    /** facilities 가 null 이면 상세를 못 받은 것, 빈 목록이면 받았는데 적힌 항목이 없는 것 */
    public record Place(String contentId, String name, String address, Double lat, Double lng,
                        List<Fact> facts) {}

    public record Fact(String label, String text) {}

    /**
     * @param note 이 목록이 무엇의 목록인지 한 줄. 여행지 그대로면 null.
     *             시군구에서 못 찾아 시도로 넓혔을 때 그 사실을 적는다 —
     *             여수 화면에 담양 야영장을 올려 놓고 아무 말도 안 하면 거짓말이 된다.
     */
    public record Section<T>(Status status, int count, List<T> items, String note) {
        public Section(Status status, int count, List<T> items) { this(status, count, items, null); }
        static <T> Section<T> of(Status s) { return new Section<>(s, 0, List.of(), null); }
    }

    /** 야영장·둘레길 — 이름과 좌표만 쓴다. 응답 모양이 서로 달라 Map 그대로 받는다 */
    public record Spot(String name, String address, Double lat, Double lng) {}

    /**
     * 그 지역에 평소 하루 몇 명이 오는가 (DataLabService 실측).
     * 집중률은 0~100 눈금이라 그것만으로는 규모를 알 수 없다. 이 숫자가 눈금에 크기를 준다.
     */
    public record Visitors(String areaName, String fromYmd, String toYmd,
                           long outsidersPerDay, long localsPerDay) {}

    public record AreaInfo(String areaName, String baseYm,
                           Section<Hub> hubs, Section<Place> pet, Section<Place> barrierFree,
                           Section<Spot> camping, Section<Spot> trails, Section<Visitors> visitors) {}

    static final int SHOW = 5;
    /** 주소 칸 한 줄에 들어갈 글자 수. 넘으면 줄인다 */
    static final int LINE_MAX = 46;
    private static final int FETCH = 50;

    private final TourApiClient client;
    private final LocgoHubService hubService;
    private final OriginSearchService originSearchService;
    private final TourExtraService tourExtraService;
    private final VisitorService visitorService;

    /** 관광공사 KorService2 의 시군구 코드(법정코드와 다르다). 시도별로 한 번만 받는다 */
    private final Map<String, Map<String, String>> tourSigungu = new ConcurrentHashMap<>();

    public AreaInfo lookup(String destination, Double lat, Double lng) {
        AreaCode.Area area = resolveArea(destination, lat, lng);
        if (area == null) {
            return new AreaInfo(null, null,
                    Section.of(Status.NO_AREA), Section.of(Status.NO_AREA), Section.of(Status.NO_AREA),
                    Section.of(Status.NO_AREA), Section.of(Status.NO_AREA), Section.of(Status.NO_AREA));
        }
        String tourArea = AreaCode.tourAreaCode(area);
        YearMonth now = YearMonth.now(ZoneId.of("Asia/Seoul"));

        CompletableFuture<Object[]> hubs = CompletableFuture.supplyAsync(() -> hubs(area, now));
        CompletableFuture<String> sg = CompletableFuture.supplyAsync(() -> tourSigunguCode(tourArea, area.sigungu()));
        CompletableFuture<Section<Place>> pet = sg.thenApplyAsync(code -> places("KorPetTourService2", "detailPetTour2", tourArea, code, true));
        CompletableFuture<Section<Place>> bf = sg.thenApplyAsync(code -> places("KorWithService2", "detailWithTour2", tourArea, code, false));

        /* 야영장·둘레길·방문자수는 이 판이 유일한 소비처다. 늦으면 그 칸만 「확인되지 않음」으로
           두고 나머지를 먼저 낸다 — 하나가 느려서 판 전체가 안 뜨면 안 된다.
           야영장은 전국을 받아 거르는 구조라(TourExtraService 참고) 캐시가 빈 첫 호출이 느리다. */
        CompletableFuture<Section<Spot>> camp = later(() -> spots(tourArea, area.sigungu(), true));
        CompletableFuture<Section<Spot>> trail = later(() -> spots(tourArea, area.sigungu(), false));
        CompletableFuture<Section<Visitors>> vis = later(() -> visitors(area, tourArea));

        Object[] h = hubs.join();
        @SuppressWarnings("unchecked") Section<Hub> hubSection = (Section<Hub>) h[1];
        return new AreaInfo(area.fullName(), (String) h[0], hubSection, pet.join(), bf.join(),
                camp.join(), trail.join(), vis.join());
    }

    /** 늦거나 깨지면 「확인되지 않음」. 없다고 말하지 않는다 — 안 받은 것과 없는 것은 다르다 */
    private <T> CompletableFuture<Section<T>> later(java.util.function.Supplier<Section<T>> f) {
        return CompletableFuture.supplyAsync(f)
                .exceptionally(e -> Section.of(Status.UNAVAILABLE))
                .completeOnTimeout(Section.of(Status.UNAVAILABLE), 7, java.util.concurrent.TimeUnit.SECONDS);
    }

    /* ── 야영장 · 둘레길 ─────────────────────────────────────── */

    private Section<Spot> spots(String tourArea, String sigungu, boolean camping) {
        if (tourArea == null) return Section.of(Status.NO_AREA);
        List<Map<String, Object>> rows = camping
                ? tourExtraService.camping(tourArea, FETCH)
                : tourExtraService.trails(tourArea, FETCH);
        if (rows.isEmpty()) return new Section<>(Status.NONE, 0, List.of());

        /* 고캠핑·두루누비는 지역으로 거르는 요청 파라미터가 없어 시도로만 걸러져 온다.
           그래서 여수 화면에 담양 야영장이 올라왔다 — 차로 두 시간 거리다.
           주소에 시군구 이름이 들어 있으면 그것으로 한 번 더 좁힌다.
           좁혀서 하나도 안 남으면 넓은 목록을 그대로 쓰되 무엇의 목록인지 적는다.
           없는 것보다는 낫지만, 여수 것인 척하면 안 된다. */
        String note = null;
        String near = idusw.sbb.checkin.domain.route.RegionMatch.core(sigungu);
        if (!near.isBlank()) {
            List<Map<String, Object>> narrowed = rows.stream()
                    .filter(m -> {
                        String a = str(m, "address");
                        return a != null && a.contains(near);
                    })
                    .toList();
            if (!narrowed.isEmpty()) rows = narrowed;
            else note = sigungu + "에는 없어 " + "같은 시도 안에서 보여 드립니다";
        }
        List<Spot> out = rows.stream().limit(SHOW)
                /* 열쇠 이름은 "addr" 가 아니라 "address" 다(TourExtraService.simple).
                   틀린 이름으로 꺼내면 null 이 오고, 화면은 주소 줄을 통째로 안 그린다.
                   오류도 안 난다 — 서버를 띄워 실제 응답을 보고 나서야 알았다. */
                .map(m -> new Spot(str(m, "name"), oneLine(str(m, "address")),
                        dbl(m, "lat"), dbl(m, "lon")))
                .filter(sp -> sp.name() != null && !sp.name().isBlank())
                .toList();
        return out.isEmpty() ? new Section<>(Status.NONE, 0, List.of())
                             : new Section<>(Status.OK, rows.size(), out, note);
    }

    /**
     * 주소 칸에 한 줄만 남긴다.
     *
     * <p>야영장은 주소(「부산광역시 기장군 …」)가 오는데, 둘레길은 주소 항목이 없어
     * 코스 설명이 대신 온다. 그것도 <b>{@code <br>} 태그가 섞인 서너 문단</b>이다.
     * 그대로 넘기면 화면이 글 덩어리에 밀리고, 태그는 이스케이프되어
     * {@code &lt;br&gt;} 글자로 보인다. 실제 응답을 보고 나서 알았다.
     *
     * <p>버리지는 않는다 — 둘레길은 그 설명이 유일한 설명이다. 첫 마디만 남긴다.
     */
    static String oneLine(String s) {
        if (s == null) return null;
        /* 주의 — 자바 15부터 문자열 안의 \s 는 「공백 한 칸」이다. 정규식의 공백류가
           아니다. 한 겹으로 적으면 오류도 없이 다른 뜻이 된다. \\s 로 적는다. */
        String t = s.replaceAll("(?i)<br\\s*/?>", " ")   // 줄바꿈 태그는 띄어쓰기로
                    .replaceAll("<[^>]*>", "")            // 남은 태그 제거
                    .replaceAll("[\\r\\n\\t]", " ")
                    .replaceAll("\\s+", " ")
                    .replaceAll("^[-·•\\s]+", "")   // 「- 」로 시작하는 목록 기호
                    .trim();
        if (t.isEmpty()) return null;
        if (t.length() <= LINE_MAX) return t;
        /* 자를 때 말 중간에서 끊지 않는다. 가까운 띄어쓰기에서 끊고 말줄임표를 붙인다 */
        int cut = t.lastIndexOf(' ', LINE_MAX);
        return t.substring(0, cut > LINE_MAX / 2 ? cut : LINE_MAX).trim() + "…";
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static Double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return (v instanceof Number num) ? num.doubleValue() : null;
    }

    /* ── 평소 방문자 수 ──────────────────────────────────────── */

    /**
     * 집중률은 0~100 눈금이다. 「74」가 몇 명인지는 말해 주지 않는다.
     * 관광 데이터랩은 과거 실측이라 두 달쯤 늦게 올라온다 — 최근 날짜를 부르면 빈 답이 온다.
     * 그래서 지난달이 아니라 <b>석 달 전 한 주</b>를 부르고, 받은 날짜를 그대로 화면에 적는다.
     * 「지난주」라고 적으면 거짓말이 된다.
     */
    private Section<Visitors> visitors(AreaCode.Area area, String tourArea) {
        java.time.LocalDate end = java.time.LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(3);
        java.time.LocalDate start = end.minusDays(6);
        DateTimeFormatter ymd = DateTimeFormatter.ofPattern("yyyyMMdd");

        List<idusw.sbb.checkin.domain.tour.dto.VisitorCount> rows;
        try {
            rows = visitorService.daily(start.format(ymd), end.format(ymd), 400, 1);
        } catch (Exception e) {
            return Section.of(Status.UNAVAILABLE);
        }
        if (rows == null || rows.isEmpty()) return Section.of(Status.UNAVAILABLE);

        /* 시도 이름으로 고른다. 데이터랩 지역코드는 KorService2 것과 다르다.
           이름도 그대로 안 맞는다 — 데이터랩은 「경상북도」, 우리 시도는 「경북」이라
           글자가 하나도 안 겹친다. 실제로 한 건도 안 맞아 빈 줄이 나왔다.
           적히는 꼴을 모아 둔 표(TourExtraService.fragmentsFor)를 같이 쓴다. 표는 하나여야 한다. */
        List<String> keys = new ArrayList<>(List.of(TourExtraService.fragmentsFor(tourArea)));
        keys.add(idusw.sbb.checkin.domain.route.RegionMatch.core(area.sido()));

        double out = 0, loc = 0;
        int outN = 0, locN = 0;
        for (var r : rows) {
            if (r.areaName() == null) continue;
            boolean mine = false;
            for (String k : keys) if (!k.isBlank() && r.areaName().contains(k)) { mine = true; break; }
            if (!mine) continue;
            if (r.visitorType() != null && r.visitorType().contains("외지")) { out += r.count(); outN++; }
            else { loc += r.count(); locN++; }
        }
        if (outN == 0 && locN == 0) return new Section<>(Status.NONE, 0, List.of());

        Visitors v = new Visitors(area.sido(), start.format(ymd), end.format(ymd),
                outN == 0 ? 0 : Math.round(out / outN),
                locN == 0 ? 0 : Math.round(loc / locN));
        return new Section<>(Status.OK, 1, List.of(v));
    }

    /* 여행지가 「부산」처럼 시도만이면 AreaCode 는 첫 시군구를 고른다. 좌표가 있으면 그 시군구로 바로잡는다 */
    public AreaCode.Area resolveArea(String destination, Double lat, Double lng) {
        AreaCode.Area dest = AreaCode.find(destination);
        if (lat == null || lng == null) return dest;
        String region = originSearchService.regionAt(lat, lng);
        AreaCode.Area here = region == null ? null : AreaCode.find(region);
        if (here == null) return dest;
        if (dest != null && !dest.sido().equals(here.sido())) return dest;
        return here;
    }

    /* ── 거점 관광지 ─────────────────────────────────────────── */

    private Object[] hubs(AreaCode.Area area, YearMonth now) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMM");
        for (int back = 1; back <= 2; back++) {
            String ym = now.minusMonths(back).format(f);
            List<HubSpot> list = hubService.hubList(ym, area.areaCd(), area.signguCd(), 20, 1);
            List<Hub> out = list.stream()
                    .filter(s -> !"숙박".equals(s.category()))
                    .sorted(java.util.Comparator.comparingInt(HubSpot::rank))
                    .limit(SHOW)
                    .map(s -> new Hub(s.name(), s.category(), s.rank(), s.lat(), s.lon()))
                    .toList();
            if (!out.isEmpty()) return new Object[]{ym, new Section<>(Status.OK, out.size(), out)};
        }
        /* 거점 서비스는 실패와 0건을 가르지 않고 빈 목록을 준다. 화면은 이 줄을 감춘다 */
        return new Object[]{null, Section.<Hub>of(Status.UNAVAILABLE)};
    }

    /* ── 반려동물 · 무장애 ───────────────────────────────────── */

    private Section<Place> places(String service, String detailOp, String tourArea, String sigungu, boolean pet) {
        if (tourArea == null) return Section.of(Status.NO_AREA);
        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", String.valueOf(FETCH));
        p.put("pageNo", "1");
        p.put("arrange", "A");
        p.put("areaCode", tourArea);
        if (sigungu != null) p.put("sigunguCode", sigungu);

        Optional<JsonNode> got = client.tryItems(service, "areaBasedList2", p);
        if (got.isEmpty()) return Section.of(Status.UNAVAILABLE);
        List<JsonNode> rows = new ArrayList<>();
        JsonNode items = got.get();
        if (items.isObject()) rows.add(items); else items.forEach(rows::add);
        if (rows.isEmpty()) return new Section<>(Status.NONE, 0, List.of());

        List<CompletableFuture<Place>> top = rows.stream().limit(SHOW)
                .map(n -> CompletableFuture.supplyAsync(() -> toPlace(n, service, detailOp, pet)))
                .toList();
        List<Place> out = top.stream().map(CompletableFuture::join).toList();
        return new Section<>(Status.OK, rows.size(), out);
    }

    private Place toPlace(JsonNode n, String service, String detailOp, boolean pet) {
        String id = n.path("contentid").asText("");
        Map<String, String> p = new LinkedHashMap<>();
        p.put("contentId", id);
        Optional<JsonNode> d = id.isBlank() ? Optional.empty() : client.tryItems(service, detailOp, p);
        List<Fact> facts = null;   // 못 받았다
        if (d.isPresent()) {
            JsonNode one = d.get().isArray() ? (d.get().isEmpty() ? null : d.get().get(0)) : d.get();
            facts = one == null ? List.of() : (pet ? petFacts(one) : barrierFacts(one));
        }
        return new Place(id,
                n.path("title").asText(""),
                n.path("addr1").asText(""),
                num(n, "mapy"), num(n, "mapx"),
                facts);
    }

    private static final String[][] BARRIER = {
            {"wheelchair", "휠체어"}, {"route", "접근로"}, {"parking", "주차"}, {"publictransport", "대중교통"},
            {"exit", "출입구"}, {"elevator", "엘리베이터"}, {"restroom", "화장실"}, {"ticketoffice", "매표소"},
            {"stroller", "유아차"}, {"lactationroom", "수유실"}, {"braileblock", "점자블록"},
            {"guidehuman", "안내 요원"}, {"audioguide", "음성 안내"}, {"signguide", "수어 안내"},
            {"helpdog", "보조견"}, {"handicapetc", "기타"}
    };

    private static final String[][] PET = {
            {"acmpyTypeCd", "동반 구역"}, {"acmpyPsblCpam", "동반 가능"}, {"acmpyNeedMtr", "지킬 것"}, {"etcAcmpyInfo", "그 밖에"}
    };

    static List<Fact> barrierFacts(JsonNode n) { return facts(n, BARRIER); }

    static List<Fact> petFacts(JsonNode n) { return facts(n, PET); }

    /* 받은 문구를 그대로 적는다. 「무장애」 한 말로 뭉뚱그리지 않는다 */
    private static List<Fact> facts(JsonNode n, String[][] keys) {
        List<Fact> out = new ArrayList<>();
        for (String[] k : keys) {
            String v = n.path(k[0]).asText("").trim();
            if (!v.isEmpty()) out.add(new Fact(k[1], v));
        }
        return out;
    }

    /* ── 관광공사 시군구 코드 ────────────────────────────────── */

    String tourSigunguCode(String tourArea, String sigungu) {
        if (tourArea == null || sigungu == null) return null;
        Map<String, String> byName = tourSigungu.computeIfAbsent(tourArea, a -> {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("numOfRows", "100");
            p.put("pageNo", "1");
            p.put("areaCode", a);
            Map<String, String> m = new LinkedHashMap<>();
            client.tryItems("KorService2", "areaCode2", p).ifPresent(items -> {
                for (JsonNode it : items.isArray() ? items : List.of(items)) {
                    m.put(it.path("name").asText(""), it.path("code").asText(""));
                }
            });
            return m;
        });
        if (byName.isEmpty()) {
            tourSigungu.remove(tourArea);   // 실패는 담아 두지 않는다
            return null;
        }
        /* 「수원시 팔달구」는 관광공사 표에 「수원시」로 있다 */
        String first = sigungu.split(" ")[0];
        String hit = byName.get(sigungu);
        return hit != null ? hit : byName.get(first);
    }

    private static Double num(JsonNode n, String key) {
        String v = n.path(key).asText("");
        try { return v.isBlank() ? null : Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
    }
}
