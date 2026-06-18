package idusw.sbb.triplinker.domain.system.repository;

import idusw.sbb.triplinker.domain.system.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 사용자별 알림 목록 조회
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 사용자별 읽지 않은 알림 목록 조회
    Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 마이페이지 알림 팝업 - 최신순 조회
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 알림 벨 배지(미확인 개수) 표시용
    long countByUserIdAndIsReadFalse(Long userId);
}