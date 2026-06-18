package idusw.sbb.triplinker.domain.system.service;

import idusw.sbb.triplinker.domain.system.dto.NotificationResponseDto;
import idusw.sbb.triplinker.domain.system.entity.Notification;
import idusw.sbb.triplinker.domain.system.repository.NotificationRepository;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // 정지/삭제/반려/권한변경 등 어디서든 호출하는 공통 알림 발송 메서드
    @Transactional
    public void send(Long userId, String type, String title, String content) {
        notificationRepository.save(
                Notification.builder()
                        .user(userRepository.getReferenceById(userId))
                        .type(type)
                        .title(title)
                        .content(content)
                        .build()
        );
    }
    // 제미나이 추가
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getNotifications(Long userId) {
        // 1. Repository에서 유저의 알림을 최신순으로 가져옵니다.
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 2. Notification 엔티티를 NotificationResponseDto로 변환하여 리턴합니다.
        return notifications.stream()
                .map(noti -> NotificationResponseDto.builder()
                        .id(noti.getId())
                        .type(noti.getType())
                        // DTO에 message라는 필드를 만들었다면 title과 content를 합쳐서 주거나 상황에 맞게 수정하세요.
                        .message(noti.getTitle() + " - " + noti.getContent())
                        .isRead(noti.isRead())
                        .createdAt(noti.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
    // 여기까지
}