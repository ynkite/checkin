package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.post.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    // 특정 게시글의 활성 댓글 목록 조회
    Page<PostComment> findByPostIdAndStatusOrderByCreatedAtAsc(Long postId, String status, Pageable pageable);

    // ACTIVE + HIDDEN 포함 (댓글 숨김 안내를 위해 전체 반환)
    Page<PostComment> findByPostIdAndStatusInOrderByCreatedAtAsc(Long postId, java.util.List<String> statuses, Pageable pageable);

    // 특정 사용자가 작성한 댓글 목록 조회
    Page<PostComment> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);
}