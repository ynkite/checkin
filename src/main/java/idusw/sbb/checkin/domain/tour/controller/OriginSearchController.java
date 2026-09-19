package idusw.sbb.checkin.domain.tour.controller;

import idusw.sbb.checkin.domain.tour.service.OriginSearchService;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플래너 출발지 검색. /api/tour/** 는 비로그인도 열려 있다.
 * 예: /api/tour/origin-search?q=부산역&lat=35.11&lng=129.04
 */
@RestController
@RequestMapping("/api/tour")
@RequiredArgsConstructor
public class OriginSearchController {

    private final OriginSearchService originSearchService;
    private final idusw.sbb.checkin.domain.tour.service.TourAreaInfoService tourAreaInfoService;

    /**
     * 지도 화면 「이 지역 여행 정보」 — 거점 관광지 · 반려동물 동반 · 무장애를 한 번에.
     * 여행 번호가 아니라 여행지 이름과 좌표를 받는다. 비공개 여행의 목적지를 내주지 않기 위해서다.
     * 예: /api/tour/area-info?destination=경주&lat=35.83&lng=129.21
     */
    @GetMapping("/area-info")
    public ResponseEntity<ApiResponse<idusw.sbb.checkin.domain.tour.service.TourAreaInfoService.AreaInfo>> areaInfo(
            @RequestParam String destination,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(ApiResponse.success(tourAreaInfoService.lookup(destination.trim(), lat, lng)));
    }

    @GetMapping("/origin-search")
    public ResponseEntity<ApiResponse<OriginSearchService.Result>> search(
            @RequestParam String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        String t = q.trim();
        if (t.length() < 2 || t.length() > 60) {
            throw new IllegalArgumentException("출발지는 2자 이상 60자 이하로 적어 주세요.");
        }
        return ResponseEntity.ok(ApiResponse.success(originSearchService.search(t, lat, lng)));
    }
}
