package idusw.sbb.checkin.domain.calendar;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class IcsCalendarController {

    private final IcsCalendarService icsCalendarService;

    // 공개 구독 피드 — 인증 없이 열린다. 토큰이 추측 불가하므로 링크를 아는 사람만 접근.
    @GetMapping(value = "/cal/{token}.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> feed(@PathVariable String token) {
        try {
            String ics = icsCalendarService.renderIcs(token);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                    .body(ics);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 마이페이지 — 현재 구독 URL (없으면 발급)
    @GetMapping("/api/me/calendar/subscription")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSubscription(
            @AuthenticationPrincipal CustomUserDetails user, HttpServletRequest req) {
        String token = icsCalendarService.getOrCreateToken(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(links(req, token)));
    }

    // 재발급 (유출 대응)
    @PostMapping("/api/me/calendar/subscription/regenerate")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerate(
            @AuthenticationPrincipal CustomUserDetails user, HttpServletRequest req) {
        String token = icsCalendarService.regenerateToken(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("구독 링크를 재발급했습니다.", links(req, token)));
    }

    // 폐기
    @DeleteMapping("/api/me/calendar/subscription")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @AuthenticationPrincipal CustomUserDetails user) {
        icsCalendarService.revokeToken(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("구독 링크를 폐기했습니다.", null));
    }

    // 구독 URL 두 종류: webcal:// (원클릭 구독) + https:// (복사용)
    private Map<String, String> links(HttpServletRequest req, String token) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        String authority = defaultPort ? host : host + ":" + port;
        String path = "/cal/" + token + ".ics";
        return Map.of(
                "subscribeUrl", "webcal://" + authority + path,
                "httpUrl", scheme + "://" + authority + path
        );
    }
}
