package idusw.sbb.triplinker.domain.system.service;

import idusw.sbb.triplinker.domain.system.dto.NotificationResponseDto;
import idusw.sbb.triplinker.domain.system.entity.Notification;
import idusw.sbb.triplinker.domain.system.repository.NotificationRepository;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /** 정지/삭제/반려/권한변경 등 어디서든 호출하는 공통 알림 발송 메서드 */
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

    /** GET /api/notifications — 사용자 알림 목록 최신순 조회 */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(noti -> NotificationResponseDto.builder()
                        .id(noti.getId())
                        .type(noti.getType())
                        .title(noti.getTitle())      // ← message 합치기 제거
                        .content(noti.getContent())  // ← 분리해서 전달
                        .isRead(noti.isRead())
                        .createdAt(noti.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /** PATCH /api/notifications/read-all — 전체 읽음 처리 */
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /** DELETE /api/notifications/{id} — 알림 개별 삭제 */
    @Transactional
    public void deleteOne(Long notifId, Long userId) {
        // 본인 알림만 삭제 가능 (보안)
        notificationRepository.deleteByIdAndUserId(notifId, userId);
    }

}