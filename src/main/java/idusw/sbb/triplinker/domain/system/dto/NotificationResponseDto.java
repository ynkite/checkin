package idusw.sbb.triplinker.domain.system.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponseDto {
    private Long id;
    private String message;  // 알림 내용
    private String type;     // 알림 타입 (예: SYSTEM, POST_LIKE 등)
    private boolean isRead;  // 읽음 여부
    private LocalDateTime createdAt;
}

// 전체 코드 제미나이 추가