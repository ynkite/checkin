package idusw.sbb.triplinker.domain.system.repository;

import idusw.sbb.triplinker.domain.system.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // SystemServiceImpl용 - Pageable 버전 (현재 없어서 컴파일 에러 발생 중)
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // NotificationController용 - List 버전
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 벨 배지 미읽음 카운트
    long countByUserIdAndIsReadFalse(Long userId);

    // PATCH /api/notifications/read-all
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    void markAllReadByUserId(@Param("userId") Long userId);

    // DELETE /api/notifications/{id}
    void deleteByIdAndUserId(Long id, Long userId);
}