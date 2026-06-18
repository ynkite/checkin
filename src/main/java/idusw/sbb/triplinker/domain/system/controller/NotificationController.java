package idusw.sbb.triplinker.domain.system.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.system.dto.NotificationResponseDto;
import idusw.sbb.triplinker.domain.system.service.NotificationService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** GET /api/notifications — 내 알림 목록 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        List<NotificationResponseDto> result = notificationService.getNotifications(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** PATCH /api/notifications/read-all — 전체 읽음 처리 */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAll(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        notificationService.markAllRead(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** DELETE /api/notifications/{id} — 알림 개별 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOne(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        notificationService.deleteOne(id, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}