package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    @Query("SELECT pl.post FROM PostLike pl JOIN pl.post p " +
            "WHERE pl.user.id = :userId AND p.status = 'ACTIVE' " +
            "ORDER BY pl.id DESC")
    List<Post> findLikedPostsByUserId(@Param("userId") Long userId);

    // 사용자가 특정 게시글에 좋아요를 눌렀는지 확인
    boolean existsByUserIdAndPostId(Long userId, Long postId);

    // 좋아요 취소 시 대상 좋아요 레코드 조회
    Optional<PostLike> findByUserIdAndPostId(Long userId, Long postId);
}
