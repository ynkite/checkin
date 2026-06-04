package idusw.sbb.triplinker.domain.post.service;

import idusw.sbb.triplinker.domain.post.dto.PostListResponseDto;
import idusw.sbb.triplinker.domain.post.repository.PostLikeRepository;
import idusw.sbb.triplinker.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
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

}
