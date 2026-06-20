package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.place.entity.PlaceCategory;
import idusw.sbb.triplinker.domain.post.entity.PlaceReview;
import idusw.sbb.triplinker.domain.post.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {

    List<PlaceReview> findByPost_IdOrderByCreatedAtDesc(Long postId);

    // 특정 장소의 PlaceReview 목록 (별점·한줄평 포함, 최신순)
    List<PlaceReview> findByPlace_IdOrderByCreatedAtDesc(Long placeId);

    // 카테고리별 장소 카드: [placeId, name, category, avgRating, reviewCount]

    // 기본(별점순) — 인기 장소/메인페이지에서 사용
    @Query("""
        SELECT pr.place.id, pr.place.name, pr.place.category,
               AVG(pr.rating), COUNT(pr),
               (SELECT COUNT(s) FROM Scrap s WHERE s.placeId = pr.place.id)
        FROM PlaceReview pr
        WHERE pr.place.category = :cat
        GROUP BY pr.place.id, pr.place.name, pr.place.category
        ORDER BY 4 DESC, 5 DESC
        """)
    List<Object[]> findPlaceCardsByCategory(@Param("cat") PlaceCategory cat, Pageable pageable);

    // 담긴순(후기 많은 순) — COUNT(pr) DESC
    @Query("""
        SELECT pr.place.id, pr.place.name, pr.place.category,
               AVG(pr.rating), COUNT(pr),
               (SELECT COUNT(s) FROM Scrap s WHERE s.placeId = pr.place.id)
        FROM PlaceReview pr
        WHERE pr.place.category = :cat
        GROUP BY pr.place.id, pr.place.name, pr.place.category
        ORDER BY 5 DESC, 4 DESC
        """)
    List<Object[]> findPlaceCardsByCategoryOrderBySaved(@Param("cat") PlaceCategory cat, Pageable pageable);

    // 스크랩순 — 스크랩 수 DESC (ORDER BY는 SELECT 위치번호 사용: 6번째=스크랩수, 5번째=후기수)
    @Query("""
        SELECT pr.place.id, pr.place.name, pr.place.category,
               AVG(pr.rating), COUNT(pr),
               (SELECT COUNT(s) FROM Scrap s WHERE s.placeId = pr.place.id)
        FROM PlaceReview pr
        WHERE pr.place.category = :cat
        GROUP BY pr.place.id, pr.place.name, pr.place.category
        ORDER BY 6 DESC, 5 DESC
        """)
    List<Object[]> findPlaceCardsByCategoryOrderByScrap(@Param("cat") PlaceCategory cat, Pageable pageable);

    // 최신순 — 가장 최근 리뷰 작성 시각 DESC
    @Query("""
        SELECT pr.place.id, pr.place.name, pr.place.category,
               AVG(pr.rating), COUNT(pr),
               (SELECT COUNT(s) FROM Scrap s WHERE s.placeId = pr.place.id),
               MAX(pr.createdAt)
        FROM PlaceReview pr
        WHERE pr.place.category = :cat
        GROUP BY pr.place.id, pr.place.name, pr.place.category
        ORDER BY 7 DESC
        """)
    List<Object[]> findPlaceCardsByCategoryOrderByLatest(@Param("cat") PlaceCategory cat, Pageable pageable);

    // 특정 장소를 리뷰한 후기(Post) 목록
    @Query("SELECT DISTINCT pr.post FROM PlaceReview pr WHERE pr.place.id = :placeId AND pr.post IS NOT NULL")
    List<Post> findPostsByPlaceId(@Param("placeId") Long placeId, Pageable pageable);
}