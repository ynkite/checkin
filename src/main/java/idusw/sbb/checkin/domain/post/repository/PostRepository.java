package idusw.sbb.checkin.domain.post.repository;

import idusw.sbb.checkin.domain.post.entity.Post;
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

    // ORDER BY를 고정하지 않아 Pageable의 Sort(latest/likes/views 등)가 그대로 적용됨
    @Query("""
        SELECT p
        FROM Post p
        WHERE p.status = :status
          AND (
                p.category = :category
                OR p.category IS NULL
                OR p.category = ''
          )
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

    // ACTIVE + HIDDEN 포함 (관리자 뷰)
    Page<Post> findByStatusInOrderByCreatedAtDesc(List<String> statuses, Pageable pageable);

    @Query("""
        SELECT p FROM Post p
        WHERE p.status IN :statuses
          AND (p.category = :cat OR (:cat = 'ROUTE' AND (p.category IS NULL OR p.category = 'ROUTE')))
        ORDER BY p.createdAt DESC""")
    Page<Post> findByStatusInAndCategoryOrderByCreatedAtDesc(
            @Param("statuses") List<String> statuses,
            @Param("cat") String cat,
            Pageable pageable);
    // 여기까지

    /**
     * 커뮤니티 목록. 숨긴 글은 관리자와 작성자에게만 나온다.
     * cat 이 null 이면 전체. ORDER BY 를 두지 않아 Pageable 정렬(최신·좋아요·조회)이 그대로 먹는다.
     */
    @Query("""
        SELECT p FROM Post p
        WHERE (p.status = 'ACTIVE'
               OR (p.status = 'HIDDEN' AND (:admin = true OR p.user.id = :viewerId)))
          AND (:cat IS NULL OR p.category = :cat
               OR (:cat = 'ROUTE' AND (p.category IS NULL OR p.category = '')))
        """)
    Page<Post> findVisible(@Param("viewerId") Long viewerId,
                           @Param("admin") boolean admin,
                           @Param("cat") String cat,
                           Pageable pageable);

    /** 관리자 게시글 목록. status 가 null 이면 ACTIVE·HIDDEN 전부, keyword 는 제목·작성자 이름. */
    @Query("""
        SELECT p FROM Post p JOIN p.user u
        WHERE ((:status IS NULL AND p.status IN ('ACTIVE', 'HIDDEN')) OR p.status = :status)
          AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') OR u.name LIKE CONCAT('%', :keyword, '%'))
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findForAdmin(@Param("status") String status,
                            @Param("keyword") String keyword,
                            Pageable pageable);
}