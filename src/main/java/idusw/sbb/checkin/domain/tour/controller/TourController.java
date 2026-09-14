package idusw.sbb.checkin.domain.tour.controller;

import idusw.sbb.checkin.domain.tour.dto.*;
import idusw.sbb.checkin.domain.tour.service.*;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 관광공사 API 를 화면에서 실호출하게 여는 엔드포인트 (이수환).
 * 목적: 제품이 돌 때 관광공사 API 가 실제로 불려 호출 이력이 쌓이게 한다
 * (미활용 시 심사 제외). 동선·예산 엔진의 깊은 통합 전이라도 이력은 확보된다.
 *
 * 응답은 로컬 DB 에 영구 저장하지 않는다 — 요청마다 실시간 호출.
 */
@RestController
@RequestMapping("/api/tour")
@RequiredArgsConstructor
public class TourController {

    private final KorTourService korTourService;
    private final RelatedTourService relatedTourService;
    private final LocgoHubService locgoHubService;
    private final ConcentrationService concentrationService;
    private final VisitorService visitorService;
    private final LodgingRateService lodgingRateService;

    /** 지역 기반 관광지·숙소 목록 (KorService2). */
    @GetMapping("/spots")
    public ResponseEntity<ApiResponse<List<Spot>>> spots(
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) Integer contentTypeId,
            @RequestParam(defaultValue = "10") int numOfRows,
            @RequestParam(defaultValue = "1") int pageNo) {
        return ResponseEntity.ok(ApiResponse.success(
                korTourService.areaBasedList(areaCode, contentTypeId, numOfRows, pageNo)));
    }

    /** 연관 관광지 — 대안 후보 (TarRlteTarService1). */
    @GetMapping("/related")
    public ResponseEntity<ApiResponse<List<RelatedSpot>>> related(
            @RequestParam String baseYm,
            @RequestParam String areaCd,
            @RequestParam String signguCd,
            @RequestParam(defaultValue = "10") int numOfRows,
            @RequestParam(defaultValue = "1") int pageNo) {
        return ResponseEntity.ok(ApiResponse.success(
                relatedTourService.relatedList(baseYm, areaCd, signguCd, numOfRows, pageNo)));
    }

    /** 중심(허브) 관광지 (LocgoHubTarService1). */
    @GetMapping("/hubs")
    public ResponseEntity<ApiResponse<List<HubSpot>>> hubs(
            @RequestParam String baseYm,
            @RequestParam String areaCd,
            @RequestParam String signguCd,
            @RequestParam(defaultValue = "10") int numOfRows,
            @RequestParam(defaultValue = "1") int pageNo) {
        return ResponseEntity.ok(ApiResponse.success(
                locgoHubService.hubList(baseYm, areaCd, signguCd, numOfRows, pageNo)));
    }

    /** 집중률 예측 — 붐빔 (TatsCnctrRateService). */
    @GetMapping("/concentration")
    public ResponseEntity<ApiResponse<List<ConcentrationRate>>> concentration(
            @RequestParam String areaCd,
            @RequestParam String signguCd,
            @RequestParam(defaultValue = "10") int numOfRows,
            @RequestParam(defaultValue = "1") int pageNo) {
        return ResponseEntity.ok(ApiResponse.success(
                concentrationService.predict(areaCd, signguCd, numOfRows, pageNo)));
    }

    /** 지역별 방문자 수 — 평소 기준선 (DataLabService). */
    @GetMapping("/visitors")
    public ResponseEntity<ApiResponse<List<VisitorCount>>> visitors(
            @RequestParam String startYmd,
            @RequestParam String endYmd,
            @RequestParam(defaultValue = "25") int numOfRows,
            @RequestParam(defaultValue = "1") int pageNo) {
        return ResponseEntity.ok(ApiResponse.success(
                visitorService.daily(startYmd, endYmd, numOfRows, pageNo)));
    }

    /** 숙박 1박 요금 추정 — 확정/추정 + 성수기 (홍은표 예산 엔진용). */
    @GetMapping("/lodging-rate")
    public ResponseEntity<ApiResponse<LodgingRate>> lodgingRate(
            @RequestParam String contentId,
            @RequestParam(required = false) String areaCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn) {
        return ResponseEntity.ok(ApiResponse.success(
                lodgingRateService.nightly(contentId, areaCode, checkIn)));
    }
}
