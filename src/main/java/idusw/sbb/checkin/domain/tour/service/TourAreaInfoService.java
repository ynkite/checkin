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

    public record Section<T>(Status status, int count, List<T> items) {
        static <T> Section<T> of(Status s) { return new Section<>(s, 0, List.of()); }
    }

    public record AreaInfo(String areaName, String baseYm,
                           Section<Hub> hubs, Section<Place> pet, Section<Place> barrierFree) {}

    static final int SHOW = 5;
    private static final int FETCH = 50;

    private final TourApiClient client;
    private final LocgoHubService hubService;
    private final OriginSearchService originSearchService;

    /** 관광공사 KorService2 의 시군구 코드(법정코드와 다르다). 시도별로 한 번만 받는다 */
    private final Map<String, Map<String, String>> tourSigungu = new ConcurrentHashMap<>();

    public AreaInfo lookup(String destination, Double lat, Double lng) {
        AreaCode.Area area = resolveArea(destination, lat, lng);
        if (area == null) {
            return new AreaInfo(null, null, Section.of(Status.NO_AREA), Section.of(Status.NO_AREA), Section.of(Status.NO_AREA));
        }
        String tourArea = AreaCode.tourAreaCode(area);
        YearMonth now = YearMonth.now(ZoneId.of("Asia/Seoul"));

        CompletableFuture<Object[]> hubs = CompletableFuture.supplyAsync(() -> hubs(area, now));
        CompletableFuture<String> sg = CompletableFuture.supplyAsync(() -> tourSigunguCode(tourArea, area.sigungu()));
        CompletableFuture<Section<Place>> pet = sg.thenApplyAsync(code -> places("KorPetTourService2", "detailPetTour2", tourArea, code, true));
        CompletableFuture<Section<Place>> bf = sg.thenApplyAsync(code -> places("KorWithService2", "detailWithTour2", tourArea, code, false));

        Object[] h = hubs.join();
        @SuppressWarnings("unchecked") Section<Hub> hubSection = (Section<Hub>) h[1];
        return new AreaInfo(area.fullName(), (String) h[0], hubSection, pet.join(), bf.join());
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
