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

    @Query("""
        SELECT pr.place.id, pr.place.name, pr.place.category,
               AVG(pr.rating), COUNT(pr),
               (SELECT COUNT(s) FROM Scrap s WHERE s.placeId = pr.place.id)
        FROM PlaceReview pr
        WHERE pr.place.category = :cat
        GROUP BY pr.place.id, pr.place.name, pr.place.category
        ORDER BY AVG(pr.rating) DESC, COUNT(pr) DESC
        """)
    List<Object[]> findPlaceCardsByCategory(@Param("cat") PlaceCategory cat, Pageable pageable);

    // 특정 장소를 리뷰한 후기(Post) 목록
    @Query("SELECT DISTINCT pr.post FROM PlaceReview pr WHERE pr.place.id = :placeId AND pr.post IS NOT NULL")
    List<Post> findPostsByPlaceId(@Param("placeId") Long placeId, Pageable pageable);
}