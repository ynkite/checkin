package idusw.sbb.triplinker.domain.system.repository;

import idusw.sbb.triplinker.domain.system.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 관리자 신고 목록 - 상태별(PENDING/REJECTED/RESOLVED) 필터링 + 페이징
    Page<Report> findByStatusOrderByIdDesc(String status, Pageable pageable);

    // 대시보드 - 신고 미처리 건수
    long countByStatus(String status);
}