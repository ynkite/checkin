package idusw.sbb.checkin.domain.crowd;

import idusw.sbb.checkin.domain.crowd.dto.CrowdForecast;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 혼잡도 — 그 날, 그 장소에 사람이 얼마나 있을까.
 *
 *   GET /api/crowd/day?region=부산 해운대구&date=2026-09-19&places=미포,해운대 해수욕장
 *   GET /api/crowd/timeline?region=&place=          한 장소의 30일 흐름
 *   GET /api/crowd/quiet?region=&date=&limit=       그 날 한적한 곳
 *   GET /api/crowd/now?region=&place=&lat=&lng=     지금 값 (SK 키가 있으면)
 *
 * region 은 이름으로 받는다 — 「부산 해운대구」 · 「해운대」 · 「부산」.
 * 코드를 화면이 알아야 할 이유가 없다.
 */
@RestController
@RequestMapping("/api/crowd")
@RequiredArgsConstructor
public class CrowdController {

    private final CrowdService crowdService;
    private final SkRealtimeCrowdClient sk;

    @GetMapping("/day")
    public ResponseEntity<ApiResponse<List<CrowdForecast>>> day(
            @RequestParam String region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String places) {

        AreaCode.Area a = AreaCode.find(region);
        List<String> names = split(places);
        if (a == null) {
            return ResponseEntity.ok(ApiResponse.success(names.stream()
                    .map(n -> CrowdForecast.unknown(n, null, "지원하지 않는 지역입니다"))
                    .toList()));
        }
        if (!AreaCode.hasCrowdData(a)) {
            return ResponseEntity.ok(ApiResponse.success(names.stream()
                    .map(n -> CrowdForecast.unknown(n, null,
                            a.sido() + "은 관광공사 집중률 예측 대상이 아닙니다"))
                    .toList()));
        }
        return ResponseEntity.ok(ApiResponse.success(
                crowdService.forecast(a.areaCd(), a.signguCd(), names,
                        date != null ? date : LocalDate.now())));
    }

    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<List<CrowdForecast>>> timeline(
            @RequestParam String region, @RequestParam String place) {
        AreaCode.Area a = AreaCode.find(region);
        if (a == null || !AreaCode.hasCrowdData(a)) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        return ResponseEntity.ok(ApiResponse.success(
                crowdService.timeline(a.areaCd(), a.signguCd(), place)));
    }

    @GetMapping("/quiet")
    public ResponseEntity<ApiResponse<List<CrowdForecast>>> quiet(
            @RequestParam String region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "6") int limit) {
        AreaCode.Area a = AreaCode.find(region);
        if (a == null || !AreaCode.hasCrowdData(a)) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        return ResponseEntity.ok(ApiResponse.success(
                crowdService.quietest(a.areaCd(), a.signguCd(),
                        date != null ? date : LocalDate.now(), limit)));
    }

    /** 지금 값. SK 키가 있으면 실시간, 없으면 오늘 예측으로 답하고 그렇다고 말한다. */
    @GetMapping("/now")
    public ResponseEntity<ApiResponse<Map<String, Object>>> now(
            @RequestParam String region,
            @RequestParam String place,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {

        Map<String, Object> out = new LinkedHashMap<>();
        SkRealtimeCrowdClient.Live live = sk.now(place, lat, lng);
        if (live != null) {
            CrowdLevel lv = CrowdLevel.of(live.asRate());
            out.put("source", "SK");
            out.put("rate", Math.round(live.asRate() * 10) / 10.0);
            out.put("levelKey", lv == null ? null : lv.key());
            out.put("levelLabel", lv == null ? null : lv.label());
            out.put("headcount", live.headcount());
            out.put("note", "지금 값입니다.");
            return ResponseEntity.ok(ApiResponse.success(out));
        }

        AreaCode.Area a = AreaCode.find(region);
        if (a == null || !AreaCode.hasCrowdData(a)) {
            out.put("source", "NONE");
            out.put("note", "이 지역은 혼잡도 자료가 없습니다.");
            return ResponseEntity.ok(ApiResponse.success(out));
        }
        CrowdForecast f = crowdService.forecast(a.areaCd(), a.signguCd(), place, LocalDate.now());
        out.put("source", f.rate() == null ? "NONE" : "TOUR");
        out.put("rate", f.rate());
        out.put("levelKey", f.levelKey());
        out.put("levelLabel", f.levelLabel());
        out.put("headcount", null);
        out.put("note", f.rate() == null
                ? (f.note() == null ? "혼잡도 자료가 없습니다." : f.note())
                : (sk.ready() ? "지금 값을 못 받아 오늘 예측으로 답합니다."
                              : "오늘 예측값입니다. 실시간 인원은 아직 연동 전입니다."));
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    /** 화면의 지역 고르기 칸 — 시도 하나의 시군구 목록. */
    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> regions(
            @RequestParam(required = false) String sido) {
        List<AreaCode.Area> list = sido == null || sido.isBlank()
                ? List.of() : AreaCode.sigungus(sido);
        return ResponseEntity.ok(ApiResponse.success(list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sido", a.sido());
            m.put("sigungu", a.sigungu());
            m.put("name", a.fullName());
            m.put("crowdData", AreaCode.hasCrowdData(a));
            return m;
        }).toList()));
    }

    private List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
