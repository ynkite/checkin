package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.route.engine.TourApiCandidateProvider;
import idusw.sbb.checkin.domain.tour.client.TourApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 동선 엔진의 관광공사 후보 소싱 — {@link TourApiCandidateProvider.Fetcher} 의 스프링 쪽 구현.
 * 쓰려면 {@code new TourApiCandidateProvider(tourCandidateFetcher)} 로 엔진에 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class TourCandidateFetcher implements TourApiCandidateProvider.Fetcher {

    private static final int ROWS = 100;

    private final TourApiClient client;
    private final TourAreaInfoService areaInfo;

    /** 지역을 모르면 빈 목록(후보 없음), 호출이 실패하면 null */
    @Override
    public List<TourApiCandidateProvider.Row> fetch(String region, String contentTypeId) {
        AreaCode.Area area = AreaCode.find(region);
        String tourArea = AreaCode.tourAreaCode(area);
        if (tourArea == null) return List.of();

        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", String.valueOf(ROWS));
        p.put("pageNo", "1");
        p.put("arrange", "O");            // 대표 이미지가 있는 것부터 — 소개가 갖춰진 곳이 먼저 온다
        p.put("areaCode", tourArea);
        p.put("contentTypeId", contentTypeId);
        String sg = areaInfo.tourSigunguCode(tourArea, area.sigungu());
        if (sg != null) p.put("sigunguCode", sg);

        Optional<JsonNode> got = client.tryItems("KorService2", "areaBasedList2", p);
        if (got.isEmpty()) return null;
        List<TourApiCandidateProvider.Row> out = new ArrayList<>();
        JsonNode items = got.get();
        for (JsonNode n : items.isArray() ? items : List.of(items)) {
            out.add(new TourApiCandidateProvider.Row(
                    n.path("contentid").asText(""), n.path("title").asText(""),
                    n.path("mapx").asText(""), n.path("mapy").asText(""), n.path("cat3").asText("")));
        }
        return out;
    }
}
