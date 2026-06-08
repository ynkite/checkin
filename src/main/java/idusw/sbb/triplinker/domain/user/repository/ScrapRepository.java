/**
 * 장소 스크랩 Repository
 * - PLACE_SCRAPS 테이블에서 사용자의 장소 스크랩 목록을 조회한다.
 * - 숙소, 맛집, 관광지, 카페 카테고리별 페이징 조회를 지원한다.
 */
package idusw.sbb.triplinker.domain.user.repository;

import idusw.sbb.triplinker.domain.user.entity.Scrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {
    Page<Scrap> findByUserIdAndCategory(Long userId, String category, Pageable pageable);
}