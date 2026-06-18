package idusw.sbb.triplinker.domain.system.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.system.dto.NotificationResponseDto;
import idusw.sbb.triplinker.domain.system.service.NotificationService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 제미나이 추가
        // 비로그인 상태일 경우 널포인터 에러(NPE) 방지
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        // 여기까지
        // 로그인한 유저의 알림을 가져오는 로직
        List<NotificationResponseDto> result = notificationService.getNotifications(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}