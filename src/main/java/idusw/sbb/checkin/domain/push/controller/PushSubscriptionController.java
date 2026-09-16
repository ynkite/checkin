package idusw.sbb.checkin.domain.push.controller;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.push.service.PushNotificationService;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushNotificationService pushService;

    // 구독에 필요한 VAPID 공개키 (비밀 아님)
    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<Map<String, String>>> publicKey() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("publicKey", pushService.getPublicKey())));
    }

    // 구독 등록 — 브라우저 PushSubscription.toJSON() 형태: {endpoint, keys:{p256dh, auth}}
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<Void>> subscribe(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody Map<String, Object> body) {
        try {
            String endpoint = (String) body.get("endpoint");
            @SuppressWarnings("unchecked")
            Map<String, String> keys = (Map<String, String>) body.get("keys");
            if (endpoint == null || keys == null || keys.get("p256dh") == null || keys.get("auth") == null) {
                return ResponseEntity.ok(ApiResponse.error("구독 정보가 올바르지 않습니다."));
            }
            pushService.subscribe(user.getUserId(), endpoint, keys.get("p256dh"), keys.get("auth"));
            return ResponseEntity.ok(ApiResponse.success("알림 구독이 완료되었습니다.", null));
        } catch (ClassCastException e) {
            return ResponseEntity.ok(ApiResponse.error("구독 정보 형식이 올바르지 않습니다."));
        }
    }

    // 구독 해제
    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestBody Map<String, String> body) {
        String endpoint = body.get("endpoint");
        if (endpoint != null) pushService.unsubscribe(endpoint);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 테스트 발송 (내 기기로)
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> test(@AuthenticationPrincipal CustomUserDetails user) {
        pushService.sendToUser(user.getUserId(), "체크인", "푸시 알림이 정상 동작합니다.", "/");
        return ResponseEntity.ok(ApiResponse.success("테스트 알림을 보냈습니다.", null));
    }
}
