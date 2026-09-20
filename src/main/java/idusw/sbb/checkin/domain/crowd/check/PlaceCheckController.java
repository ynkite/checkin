package idusw.sbb.checkin.domain.crowd.check;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/crowd/check  {"tripId": 1, "lat": 35.1, "lng": 129.1}
 * 실시간 화면의 「장소 확인하기」. 로그인한 사람이 자기(또는 함께하는) 여행만 확인한다.
 */
@RestController
@RequestMapping("/api/crowd")
@RequiredArgsConstructor
public class PlaceCheckController {

    public record CheckRequest(Long tripId, Double lat, Double lng) {}

    private final PlaceCheckService placeCheckService;
    private final PlaceCheckQuota quota;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<PlaceCheck.Result>> check(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody CheckRequest req) {
        if (req == null || req.tripId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("어느 여행을 확인할지 알려 주세요."));
        }
        if (user == null || !placeCheckService.canCheck(req.tripId(), user.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("이 여행을 확인할 수 없습니다."));
        }
        /* 하루 몇 번까지. 이 단추 하나가 유료 API 와 관광공사 집중률을 부른다.
           세는 곳은 여기다 — 화면에서만 막으면 의미가 없다.
           부르기 전에 센다. 부르고 나서 세면 이미 돈이 나간 뒤다. */
        PlaceCheckQuota.Verdict q = quota.use(user.getUserId());
        if (!q.allowed()) {
            /* 왜 못 누르는지와 언제 다시 되는지를 같이 말한다.
               「잠시 후 다시」처럼 모호하게 쓰지 않는다 — 언제까지 기다릴지 모른다. */
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(
                    "오늘 확인은 " + q.limit() + "번까지입니다. 내일 다시 확인할 수 있습니다."));
        }

        /* 위치가 이상하면 모르는 것으로 둔다 */
        boolean pos = req.lat() != null && req.lng() != null
                && Math.abs(req.lat()) <= 90 && Math.abs(req.lng()) <= 180 && !(req.lat() == 0 && req.lng() == 0);
        return ResponseEntity.ok(ApiResponse.success(
                "남은 확인 " + q.remaining() + "번",
                placeCheckService.check(req.tripId(), pos ? req.lat() : null, pos ? req.lng() : null)));
    }
}
