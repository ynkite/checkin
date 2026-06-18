package idusw.sbb.triplinker.domain.post.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.post.dto.PlaceReviewResponseDto;
import idusw.sbb.triplinker.domain.post.dto.PlaceReviewSaveDto;
import idusw.sbb.triplinker.domain.post.dto.PlaceReviewUpdateDto;
import idusw.sbb.triplinker.domain.post.dto.PostDetailResponseDto;
import idusw.sbb.triplinker.domain.post.dto.PostListResponseDto;
import idusw.sbb.triplinker.domain.post.dto.PostWriteDto;
import idusw.sbb.triplinker.domain.post.service.PostService;
import idusw.sbb.triplinker.domain.system.dto.ReportRequestDto;
import idusw.sbb.triplinker.domain.system.service.SystemService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import idusw.sbb.triplinker.global.service.LocalFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final SystemService systemService;
    private final LocalFileService localFileService;

    // 커뮤니티 - 이미지 업로드
    @PostMapping("/images")
    public ResponseEntity<List<String>> uploadImages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {
        List<String> imageUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            imageUrls.add(localFileService.save(file));
        }
        return ResponseEntity.ok(imageUrls);
    }

    // 커뮤니티 - 게시글 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostListResponseDto>>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String category
    ) {
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


//        Pageable pageable = PageRequest.of(page, size);

        Page<PostListResponseDto> posts = postService.getPosts(pageable, category);

        return ResponseEntity.ok(
                ApiResponse.success("게시글 목록 조회 성공", posts)
        );
    }

    // 마이페이지 - 내가 작성한 후기 목록 조회
    @GetMapping("/me")
    public ResponseEntity<List<PostListResponseDto>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(postService.getMyPosts(userDetails.getUserId()));
    }

    // 마이페이지 - 좋아요한 후기 목록 조회
    @GetMapping("/liked")
    public ResponseEntity<List<PostListResponseDto>> getMyLikedPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(postService.getMyLikedPosts(userDetails.getUserId()));
    }

    // 커뮤니티 - 게시글 작성
    @PostMapping
    public ResponseEntity<Long> createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PostWriteDto dto
    ) {
        Long postId = postService.createPost(userDetails.getUserId(), dto);
        return ResponseEntity.ok(postId);
    }

    // 커뮤니티 - 게시글 상세 조회
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponseDto>> getPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        Long userId = userDetails != null ? userDetails.getUserId() : null;

        PostDetailResponseDto post = postService.getPost(userId, postId);

        return ResponseEntity.ok(
                ApiResponse.success("게시글 상세 조회 성공", post)
        );
    }

    // 커뮤니티 - 게시글 수정
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<Long>> updatePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestBody PostWriteDto dto
    ) {
        Long updatedPostId = postService.updatePost(
                userDetails.getUserId(),
                postId,
                dto
        );

        return ResponseEntity.ok(
                ApiResponse.success("게시글이 수정되었습니다.", updatedPostId)
        );
    }

    // 커뮤니티 - 게시글 삭제
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        postService.deletePost(userDetails.getUserId(), postId);
        return ResponseEntity.ok().build();
    }

    // 커뮤니티 - 댓글 목록 조회
    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<PostDetailResponseDto.CommentInfo>> getComments(
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(postService.getComments(postId));
    }

    // 커뮤니티 - 댓글 작성
    @PostMapping("/{postId}/comments")
    public ResponseEntity<Long> addComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestBody PostWriteDto dto
    ) {
        Long commentId = postService.addComment(userDetails.getUserId(), postId, dto);
        return ResponseEntity.ok(commentId);
    }

    // 커뮤니티 - 좋아요 토글
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<Boolean>> toggleLikePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        boolean liked = postService.toggleLikePost(userDetails.getUserId(), postId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        liked ? "좋아요를 눌렀습니다." : "좋아요를 취소했습니다.",
                        liked
                )
        );
    }

    // 커뮤니티 - 게시글 스크랩 토글
    @PostMapping("/{postId}/scraps")
    public ResponseEntity<ApiResponse<Boolean>> toggleScrapPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestParam(required = false, defaultValue = "ROUTE") String category
    ) {
        boolean scrapped = postService.toggleScrapPost(
                userDetails.getUserId(),
                postId,
                category
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        scrapped ? "스크랩했습니다." : "스크랩을 취소했습니다.",
                        scrapped
                )
        );
    }

    // 커뮤니티 - 게시글 신고 접수
    @PostMapping("/{postId}/reports")
    public ResponseEntity<Long> reportPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestBody ReportRequestDto dto
    ) {
        ReportRequestDto reportDto = new ReportRequestDto(postId, dto.reason());
        Long reportId = systemService.reportPost(userDetails.getUserId(), reportDto);
        return ResponseEntity.ok(reportId);
    }


    // 장소 리뷰 저장
    @PostMapping("/{postId}/place-reviews")
    public ResponseEntity<Void> savePlaceReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestBody PlaceReviewSaveDto dto
    ) {
        postService.savePlaceReviews(userDetails.getUserId(), postId, dto);
        return ResponseEntity.ok().build();
    }

    // 게시글별 장소 리뷰 조회
    @GetMapping("/{postId}/place-reviews")
    public ResponseEntity<ApiResponse<List<PlaceReviewResponseDto>>> getPlaceReviewsByPost(
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("장소 리뷰 조회 성공", postService.getPlaceReviewsByPost(postId))
        );
    }

    // 장소 리뷰 수정 (별점·한줄평)
    @PatchMapping("/{postId}/place-reviews")
    public ResponseEntity<Void> updatePlaceReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestBody PlaceReviewUpdateDto dto
    ) {
        postService.updatePlaceReviews(userDetails.getUserId(), postId, dto);
        return ResponseEntity.ok().build();
    }


    // 커뮤니티 - 게시글 이미지 삭제
    @DeleteMapping("/{postId}/images")
    public ResponseEntity<ApiResponse<Void>> deletePostImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestParam String imageUrl
    ) {
        postService.deletePostImage(userDetails.getUserId(), postId, imageUrl);

        return ResponseEntity.ok(
                ApiResponse.success("게시글 이미지 삭제 성공", null)
        );
    }
}