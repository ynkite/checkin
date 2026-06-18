package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.post.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    // 특정 게시글의 이미지 목록을 표시 순서대로 조회
    List<PostImage> findByPostIdOrderByImageOrderAsc(Long postId);

    // 게시글 삭제 시 S3 Object Key 조회 등에 사용
    List<PostImage> findByPostId(Long postId);
}