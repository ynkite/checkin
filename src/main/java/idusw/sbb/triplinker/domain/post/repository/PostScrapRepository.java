package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.post.entity.PostScrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostScrapRepository extends JpaRepository<PostScrap, Long> {

    // 사용자가 특정 게시글을 스크랩했는지 확인
    boolean existsByUserIdAndPostId(Long userId, Long postId);

    // 게시글별 스크랩 수 조회
    long countByPost_Id(Long postId);

    // 스크랩 취소 시 대상 스크랩 레코드 조회
    Optional<PostScrap> findByUserIdAndPostId(Long userId, Long postId);

    // 마이페이지 - 사용자가 스크랩한 게시글 목록 조회
    Page<PostScrap> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 마이페이지 - 카테고리별 스크랩 게시글 목록 조회
    Page<PostScrap> findByUserIdAndCategoryOrderByCreatedAtDesc(Long userId, String category, Pageable pageable);
}