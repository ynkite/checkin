package idusw.sbb.checkin.domain.live;

import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 「다른 곳으로」가 부르는 자리. 후보만 돌려준다.
 *
 * <p>읽기라서 {@code /api/live} 밑에 뒀다. 실제로 일정을 바꾸는 것은
 * 기존 {@code POST /api/trips/{tripId}/routes/replace} 가 하고, 그쪽은 로그인이 필요하다.
 *
 * <p>고른 결과를 바로 반영하지 않는 이유 — 어디로 갈지는 사용자가 정한다.
 * 붐빈다고 서비스가 목적지를 바꿔 버리면 그건 제안이 아니라 통보다.
 */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class QuietAlternativeController {

    private final QuietAlternativeService quietAlternativeService;

    /**
     * @param tripId 여행
     * @param day    며칠째 (1부터)
     * @param name   바꾸려는 장소. 비우면 그날 가장 붐비는 곳
     * @param limit  몇 곳까지
     */
    @GetMapping("/quiet")
    public ResponseEntity<ApiResponse<Map<String, Object>>> quiet(
            @RequestParam Long tripId,
            @RequestParam(defaultValue = "1") int day,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                quietAlternativeService.alternatives(tripId, day, name, limit)));
    }
}
