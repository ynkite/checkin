package idusw.sbb.triplinker.domain.post.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.post.dto.PostDetailResponseDto;
import idusw.sbb.triplinker.domain.post.dto.PostResponseDto;
import idusw.sbb.triplinker.domain.post.service.PostService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 임시 골격 — 게시글 상세조회(+조회수 추적)만 구현됨.
// 글쓰기/수정/삭제/좋아요/스크랩/신고/댓글은 별도 구현 시 이 컨트롤러에 메서드만 추가하면 됨.
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 상세 조회 - 비로그인도 조회 가능, 로그인 상태면 조회 로그에 사용자 기록
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponseDto>> getPostDetail(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long viewerId = (userDetails != null) ? userDetails.getUserId() : null;
        PostDetailResponseDto detail = postService.getPostDetail(postId, viewerId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    // 제미나이 추가
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostResponseDto>>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String category) {

        // 프론트엔드에서 넘어온 sort 값을 안전하게 변환
        String sortProperty = "createdAt"; // 기본값: 최신순
        if (sort != null && !sort.isEmpty()) {
            if ("scrap".equals(sort)) {
                // Post 엔티티에 scrap 필드가 없으므로, 임시로 좋아요순(likeCount)이나 최신순으로 우회합니다.
                // 만약 나중에 Post 엔티티에 scrapCount 필드를 만드신다면 "scrapCount"로 변경하시면 됩니다.
                sortProperty = "likeCount";
            } else {
                sortProperty = sort;
            }
        }

        // Pageable 객체 생성 (안전하게 변환된 sortProperty 사용)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortProperty));

        // 서비스단에서 목록 조회 로직 호출
        Page<PostResponseDto> posts = postService.getPosts(pageable, category);

        return ResponseEntity.ok(ApiResponse.success(posts));
    }
    // 여기까지
}