package idusw.sbb.triplinker.domain.admin.repository;

import idusw.sbb.triplinker.domain.admin.entity.Curation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CurationRepository extends JpaRepository<Curation, Long> {

    // 관리자 페이지 - 노출 순서대로 목록 조회 (페이징)
    Page<Curation> findAllByOrderByDisplayOrderAsc(Pageable pageable);

    // 메인 화면 노출용 - 기본 노출이거나 노출 기간이 아직 끝나지 않은 큐레이션
    List<Curation> findByIsDefaultTrueOrEndAtAfterOrderByDisplayOrderAsc(LocalDateTime now);
}