package idusw.sbb.triplinker.domain.post.service;

import idusw.sbb.triplinker.domain.post.dto.PostDetailResponseDto;
import idusw.sbb.triplinker.domain.post.dto.PostListResponseDto;
import idusw.sbb.triplinker.domain.post.dto.PostResponseDto;
import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.entity.PostViewLog;
import idusw.sbb.triplinker.domain.post.repository.PostLikeRepository;
import idusw.sbb.triplinker.domain.post.repository.PostRepository;
import idusw.sbb.triplinker.domain.post.repository.PostViewLogRepository;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostViewLogRepository postViewLogRepository;
    private final UserRepository userRepository;

    //후기 목록 조회
    public List<PostListResponseDto> getMyPosts(Long userId) {
        return postRepository.findByUserIdAndStatusOrderByIdDesc(userId, "ACTIVE")
                .stream()
                .map(PostListResponseDto::from)
                .collect(Collectors.toList());
    }

    //좋아요한 후기 목록 조회
    public List<PostListResponseDto> getMyLikedPosts(Long userId) {
        return postLikeRepository.findLikedPostsByUserId(userId)
                .stream()
                .map(PostListResponseDto::from)
                .collect(Collectors.toList());
    }

    // 게시글 상세 조회 - 로그인한 사용자만 조회수 집계 (비로그인은 카운트하지 않음)
    @Transactional
    public PostDetailResponseDto getPostDetail(Long postId, Long viewerId) {
        Post post = postRepository.findById(postId)
                .filter(p -> !"DELETED".equals(p.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 게시글입니다."));

        if (viewerId != null) {
            post.increaseViewCount();
            postViewLogRepository.save(PostViewLog.builder()
                    .post(post)
                    .viewer(userRepository.getReferenceById(viewerId))
                    .build());
        }

        return PostDetailResponseDto.from(post);
    }

    // 제미나이 추가
    // 게시글 목록 조회 (카테고리 필터링 포함)
    public Page<PostResponseDto> getPosts(Pageable pageable, String category) {
        Page<Post> posts;

        // 카테고리가 있으면 카테고리 필터링, 없으면 전체 조회
        if (category != null && !category.isEmpty()) {
            posts = postRepository.findByCategoryAndStatus(category, "ACTIVE", pageable);
        } else {
            posts = postRepository.findByStatus("ACTIVE", pageable);
        }

        // 엔티티 Page를 DTO Page로 변환
        return posts.map(PostResponseDto::from);
    }
    // 여기까지
}