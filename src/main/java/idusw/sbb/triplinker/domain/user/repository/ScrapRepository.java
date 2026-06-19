package idusw.sbb.triplinker.domain.user.repository;

import idusw.sbb.triplinker.domain.user.entity.Scrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {
    Page<Scrap> findByUserIdAndCategory(Long userId, String category, Pageable pageable);
    List<Scrap> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);
    void deleteByUserIdAndPlaceId(Long userId, Long placeId);

    // 토글 처리용 — 이미 스크랩했는지 엔티티 자체를 조회
    Optional<Scrap> findByUserIdAndPlaceId(Long userId, Long placeId);

    // 메인페이지 카드 스크랩 수 표시용
    long countByPlaceId(Long placeId);
}