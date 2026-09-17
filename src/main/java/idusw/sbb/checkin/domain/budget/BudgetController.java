package idusw.sbb.checkin.domain.budget;

import idusw.sbb.checkin.domain.budget.dto.BudgetEstimate;
import idusw.sbb.checkin.domain.budget.dto.BudgetRequest;
import idusw.sbb.checkin.domain.budget.dto.Festival;
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
