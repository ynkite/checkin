package idusw.sbb.checkin.domain.sk;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 지오비전 퍼즐 — 화면이 부르는 자리.
 *
 * 키를 아직 안 받았어도 500 을 내지 않는다. ready=false 로 답하고
 * 화면은 그 칸을 「연동 전」으로 둔다. 심사 때 빈 화면이 뜨는 것보다
 * 「아직 안 붙었다」고 적혀 있는 쪽이 낫다.
 */
@RestController
@RequestMapping("/api/puzzle")
@RequiredArgsConstructor
public class PuzzleController {

    private final PuzzleService puzzle;
    private final idusw.sbb.checkin.global.apikey.PaidGate gate;

    /** 연동 상태. 화면이 먼저 물어보고 칸을 만들지 말지 정한다 */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ready", puzzle.ready());
        m.put("paidOpen", gate.enabled());
        m.put("dailyCap", gate.cap());
        m.put("note", !puzzle.ready()
                ? "지오비전 퍼즐은 아직 연동 전입니다. 키를 넣으면 바로 씁니다."
                : gate.enabled()
                    ? "연결돼 있습니다. 유료 상품도 열려 있습니다 (하루 " + gate.cap() + "회)."
                    : "연결돼 있습니다. 유료 상품은 닫아 뒀습니다 — "
                      + "돈이 들어서 최종 테스트 때만 엽니다. 지하철 혼잡도는 무료라 그대로 씁니다.");
        return ResponseEntity.ok(ApiResponse.success(m));
    }

    @GetMapping("/subway")
    public ResponseEntity<ApiResponse<JsonNode>> subway(
            @RequestParam String station, @RequestParam(required = false) String date) {
        return ResponseEntity.ok(ApiResponse.success(puzzle.subwayCongestion(station, date)));
    }

    @GetMapping("/place")
    public ResponseEntity<ApiResponse<JsonNode>> place(
            @RequestParam String poi, @RequestParam(required = false) String date) {
        return ResponseEntity.ok(ApiResponse.success(puzzle.placeCongestion(poi, date)));
    }

    @GetMapping("/population")
    public ResponseEntity<ApiResponse<JsonNode>> population(
            @RequestParam String area, @RequestParam(required = false) String date) {
        return ResponseEntity.ok(ApiResponse.success(puzzle.population(area, date)));
    }

    @GetMapping("/travel")
    public ResponseEntity<ApiResponse<JsonNode>> travel(
            @RequestParam String area, @RequestParam(required = false) String month) {
        return ResponseEntity.ok(ApiResponse.success(puzzle.travel(area, month)));
    }

    @GetMapping("/dining")
    public ResponseEntity<ApiResponse<JsonNode>> dining(
            @RequestParam String area, @RequestParam(required = false) String month) {
        return ResponseEntity.ok(ApiResponse.success(puzzle.dining(area, month)));
    }

    @GetMapping("/residence")
    public ResponseEntity<ApiResponse<JsonNode>> residence(
            @RequestParam String area, @RequestParam(required = false) String month) {
        return ResponseEntity.ok(ApiResponse.success(puzzle.residence(area, month)));
    }

    @GetMapping("/academy")
    public ResponseEntity<ApiResponse<JsonNode>> academy(
            @RequestParam String area, @RequestParam(required = false) String month) {
        return ResponseEntity.ok(ApiResponse.success(puzzle.academy(area, month)));
    }
}
