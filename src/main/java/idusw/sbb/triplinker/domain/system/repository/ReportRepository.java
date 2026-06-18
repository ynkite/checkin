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
    // 관리자 - 처리 상태별 신고 목록 조회
    Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    // 특정 게시글에 접수된 신고 목록 조회
    Page<Report> findByPostIdOrderByCreatedAtDesc(Long postId, Pageable pageable);

    // 특정 사용자가 접수한 신고 목록 조회
    Page<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);
}