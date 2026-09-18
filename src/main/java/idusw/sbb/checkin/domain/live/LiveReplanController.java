package idusw.sbb.checkin.domain.live;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 실시간 화면에서 동선을 고치는 자리.
 *
 * <p>경로를 {@code /api/trips/{tripId}/routes} 밑에 둔 이유 — 이 경로는 GET 만
 * 공유 링크용으로 열려 있고 POST 는 로그인이 필요하다. {@code /api/live/**} 는
 * 통째로 열려 있어서 쓰기를 거기 두면 남의 여행을 고칠 수 있게 된다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/routes")
@RequiredArgsConstructor
public class LiveReplanController {

    private final LiveReplanService liveReplanService;

    /** 붐비는 곳을 남은 구간의 뒤로 미룬다. 실시간 화면의 「순서 바꾸기」 */
    @PostMapping("/swap-crowded")
    public ResponseEntity<ApiResponse<Map<String, Object>>> swapCrowded(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    liveReplanService.swapCrowded(tripId, day, currentUserId())));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    private Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof CustomUserDetails u)) return null;
        return u.getUserId();
    }
}
