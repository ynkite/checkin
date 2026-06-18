package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    // 커뮤니티 - 게시글 목록 조회
    Page<Post> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    // 특정 여행 플랜과 연결된 게시글 목록 조회
    Page<Post> findByPlanIdAndStatusOrderByCreatedAtDesc(Long planId, String status, Pageable pageable);

    Page<Post> findByStatusAndCategoryOrderByCreatedAtDesc(String status, String category, Pageable pageable);

    @Query("""
        SELECT p
        FROM Post p
        WHERE p.status = :status
          AND (
                p.category = :category
                OR p.category IS NULL
                OR p.category = ''
          )
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findRoutePostsIncludingNullCategory(
            @Param("status") String status,
            @Param("category") String category,
            Pageable pageable
    );

    // 관리자 대시보드 - 상태별 게시글 수 집계
    long countByStatus(String status);

    // 관리자 대시보드 - 활성 게시글 전체 조회수 합계
    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Post p WHERE p.status = :status")
    long sumViewCountByStatus(@Param("status") String status);

    // 관리자 대시보드 - 조회수 기준 인기 게시글 Top5
    List<Post> findTop5ByStatusOrderByViewCountDesc(String status);

    // 제미나이 추가
    // PostRepository.java
    Page<Post> findByCategoryAndStatus(String category, String status, Pageable pageable);
    Page<Post> findByStatus(String status, Pageable pageable);
    // 여기까지
}