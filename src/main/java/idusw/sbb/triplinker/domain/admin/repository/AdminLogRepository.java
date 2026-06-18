package idusw.sbb.triplinker.domain.admin.repository;

import idusw.sbb.triplinker.domain.admin.entity.AdminLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    // 특정 관리자의 조치 이력 조회 (필요 시 화면 노출용)
    List<AdminLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);
}