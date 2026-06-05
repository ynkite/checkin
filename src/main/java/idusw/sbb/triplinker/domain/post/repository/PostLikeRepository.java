package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    @Query("SELECT pl.post FROM PostLike pl JOIN pl.post p " +
            "WHERE pl.user.id = :userId AND p.status = 'ACTIVE' " +
            "ORDER BY pl.id DESC")
    List<Post> findLikedPostsByUserId(@Param("userId") Long userId);
}
