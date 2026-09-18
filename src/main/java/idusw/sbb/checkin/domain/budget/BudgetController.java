package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.budget.dto.BudgetRequest;
import idusw.sbb.checkin.domain.budget.dto.Festival;
import idusw.sbb.checkin.domain.budget.dto.FestivalLookup;
import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 예산 — 성수기·축제를 반영한 산출.
 *
 *   POST /api/budget/estimate       숙박·식비·이동·입장료 + 근거 + 정확도
 *   GET  /api/budget/festivals      여행 기간에 열리는 축제
 *   GET  /api/budget/season         그 기간이 성수기인가
 */
@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetEstimateService budgetEstimateService;
    private final FestivalService festivalService;

    @PostMapping("/estimate")
    public ResponseEntity<ApiResponse<BudgetEstimate>> estimate(@RequestBody BudgetRequest req) {
        return ResponseEntity.ok(ApiResponse.success(budgetEstimateService.estimate(req)));
    }

    @GetMapping("/festivals")
    public ResponseEntity<ApiResponse<List<Festival>>> festivals(
            @RequestParam String region,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        String tourArea = AreaCode.tourAreaCode(AreaCode.find(region));
        if (tourArea == null) return ResponseEntity.ok(ApiResponse.success(List.of()));
        return ResponseEntity.ok(ApiResponse.success(
                festivalService.during(tourArea, from, to != null ? to : from)));
    }

    /**
     * 축제를 상태와 함께 준다 — FOUND / NONE / UNKNOWN.
     * 기존 /festivals(목록만) 는 그대로 두고, 「확인 안 됨」과 「없음」을 구분해야 하는
     * 화면이 이걸 부른다. 「없음」과 「확인 못 함」을 같은 문구로 쓰면 거짓말이 된다.
     */
    @GetMapping("/festivals/status")
    public ResponseEntity<ApiResponse<FestivalLookup>> festivalStatus(
            @RequestParam String region,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        String tourArea = AreaCode.tourAreaCode(AreaCode.find(region));
        // 지역을 못 찾으면 조회 자체가 안 되므로 「확인 안 됨」이다. 「없음」이 아니다.
        if (tourArea == null) return ResponseEntity.ok(ApiResponse.success(FestivalLookup.unknown()));
        return ResponseEntity.ok(ApiResponse.success(
                festivalService.lookup(tourArea, from, to != null ? to : from)));
    }

    @GetMapping("/season")
    public ResponseEntity<ApiResponse<BudgetEstimate.Season>> season(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate end = to != null ? to : from;
        SeasonRules.Season s = SeasonRules.strongest(from, end);
        return ResponseEntity.ok(ApiResponse.success(new BudgetEstimate.Season(
                s.key(), s.label(), s.carMultiplier(), s.foodMultiplier(),
                SeasonRules.weekendCheckIn(from),
                SeasonRules.peakDays(from, end).stream().map(LocalDate::toString).toList())));
    }
}
