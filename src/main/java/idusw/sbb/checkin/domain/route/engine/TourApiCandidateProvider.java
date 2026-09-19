package idusw.sbb.checkin.domain.route.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한국관광공사 국문 관광정보(KorService2 areaBasedList2)로 후보를 모은다.
 *
 * <p>엔진은 스프링을 모른다. 실제 호출은 {@link Fetcher} 로 넘겨받는다 —
 * 스프링 쪽 구현은 {@code domain.tour.service.TourCandidateFetcher} 다.
 *
 * <p>분류 — contentTypeId 12(관광지)는 TOUR, 39(음식점)는 cat3 가 A05020900(카페·전통찻집)이면 CAFE,
 * 아니면 FOOD. 2026-09-19 경주시 실호출로 코드를 확인했다.
 * 영업시간은 목록 API 에 없다. 모르는 값은 비워 둔다(Candidate 계약상 「항상 연다」로 읽힌다).
 *
 * <p>호출이 실패하면 빈 목록이 아니라 예외를 던진다. 빈 목록은 「이 지역에 후보가 없다」로 읽혀
 * 빈 일정이 성공으로 나가는 일이 있었다. 예외면 엔진 호출부가 기존 경로로 되돌아간다.
 */
public final class TourApiCandidateProvider implements PlaceCandidateProvider {

    static final String CAFE_CAT3 = "A05020900";

    /** 한 행 — 관광공사 응답에서 쓰는 칸만. */
    public record Row(String contentId, String title, String mapx, String mapy, String cat3) {}

    /**
     * 지역·콘텐츠 유형의 목록을 준다. 호출이 실패하면 null — 0건(빈 목록)과 구분한다.
     */
    @FunctionalInterface
    public interface Fetcher {
        List<Row> fetch(String region, String contentTypeId);
    }

    private final Fetcher fetcher;

    public TourApiCandidateProvider(Fetcher fetcher) {
        if (fetcher == null) throw new IllegalArgumentException("fetcher must not be null");
        this.fetcher = fetcher;
    }

    @Override
    public List<Candidate> findCandidates(String region, LocalDate startDate, LocalDate endDate) {
        Map<String, Candidate> byId = new LinkedHashMap<>();
        for (String type : List.of("12", "39")) {
            List<Row> rows = fetcher.fetch(region, type);
            if (rows == null) {
                throw new IllegalStateException("관광공사 후보를 받지 못했다 — region=" + region + " type=" + type);
            }
            for (Row r : rows) {
                Candidate c = toCandidate(r, type);
                if (c != null) byId.putIfAbsent(c.id(), c);
            }
        }
        return new ArrayList<>(byId.values());
    }

    static Candidate toCandidate(Row r, String type) {
        if (r == null || r.contentId() == null || r.contentId().isBlank()) return null;
        if (r.title() == null || r.title().isBlank()) return null;
        Double lat = parse(r.mapy()), lng = parse(r.mapx());
        if (lat == null || lng == null || (lat == 0 && lng == 0)) return null;
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;

        CandidateCategory cat = "12".equals(type) ? CandidateCategory.TOUR
                : CAFE_CAT3.equals(r.cat3()) ? CandidateCategory.CAFE
                : CandidateCategory.FOOD;
        return new Candidate("tour:" + r.contentId(), r.title().trim(), new GeoPoint(lat, lng), cat,
                null, null, null, null);
    }

    private static Double parse(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
