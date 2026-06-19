package idusw.sbb.triplinker.domain.post.service;

import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.place.entity.Place;
import idusw.sbb.triplinker.domain.place.service.PlaceService;
import idusw.sbb.triplinker.domain.post.dto.*;
import idusw.sbb.triplinker.domain.post.entity.*;
import idusw.sbb.triplinker.domain.post.repository.*;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.post.dto.PostDetailResponseDto;
import idusw.sbb.triplinker.global.service.LocalFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import idusw.sbb.triplinker.domain.post.entity.Post;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostScrapRepository postScrapRepository;
    private final PostImageRepository postImageRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceService placeService;
    private final UserRepository userRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final LocalFileService localFileService;

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

    // 마이페이지 - 스크랩한 여행 경로 목록 조회
    public List<PostListResponseDto> getMyScrappedRoutePosts(Long userId) {
        Pageable pageable = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"));
        return postScrapRepository.findByUserIdAndCategoryOrderByCreatedAtDesc(userId, "ROUTE", pageable)
                .stream()
                .map(PostScrap::getPost)
                .filter(post -> post != null && "ACTIVE".equals(post.getStatus()))
                .map(PostListResponseDto::from)
                .collect(Collectors.toList());
    }

    // 커뮤니티 - 게시글 목록 조회
    public Page<PostListResponseDto> getPosts(Pageable pageable, String category, String sort) {
        Page<Post> posts;

        if ("scrap".equals(sort)) {
            // Post 엔티티에 scrapCount 컬럼이 없어 DB 정렬이 불가능 → 실제 스크랩 수를 집계해 메모리에서 정렬
            posts = getPostsSortedByScrapCount(pageable, category);
        } else if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) {
            posts = postRepository.findByStatus("ACTIVE", pageable);
        } else {
            String categoryCode = toCategoryCode(category);

            if ("ROUTE".equals(categoryCode)) {
                posts = postRepository.findRoutePostsIncludingNullCategory(
                        "ACTIVE",
                        "ROUTE",
                        pageable
                );
            } else {
                posts = postRepository.findByCategoryAndStatus(
                        categoryCode,
                        "ACTIVE",
                        pageable
                );
            }
        }

        return posts.map(post -> {
            int scraps = (int) postScrapRepository.countByPost_Id(post.getId());
            List<PostImage> images = postImageRepository.findByPostIdOrderByImageOrderAsc(post.getId());
            String thumbnailUrl = images.isEmpty() ? null : images.get(0).getImageUrl();
            return PostListResponseDto.from(post, scraps, thumbnailUrl);
        });
    }

    // 스크랩 수("담긴순"/"스크랩순") 기준 정렬 — 실제 PostScrap 카운트로 메모리에서 정렬 후 직접 페이징
    private Page<Post> getPostsSortedByScrapCount(Pageable pageable, String category) {
        List<Post> all;
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) {
            all = postRepository.findByStatus("ACTIVE", Pageable.unpaged()).getContent();
        } else {
            String categoryCode = toCategoryCode(category);
            all = "ROUTE".equals(categoryCode)
                    ? postRepository.findRoutePostsIncludingNullCategory("ACTIVE", "ROUTE", Pageable.unpaged()).getContent()
                    : postRepository.findByCategoryAndStatus(categoryCode, "ACTIVE", Pageable.unpaged()).getContent();
        }

        List<Post> sorted = all.stream()
                .sorted((a, b) -> Long.compare(
                        postScrapRepository.countByPost_Id(b.getId()),
                        postScrapRepository.countByPost_Id(a.getId())))
                .toList();

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), sorted.size());
        List<Post> pageContent = (start >= sorted.size()) ? List.of() : sorted.subList(start, end);

        return new PageImpl<>(pageContent, pageable, sorted.size());
    }

    private String toCategoryCode(String category) {
        return switch (category.toLowerCase()) {
            case "stay" -> "STAY";
            case "food" -> "FOOD";
            case "tour" -> "TOUR";
            case "cafe" -> "CAFE";
            case "route" -> "ROUTE";
            default -> "ROUTE";
        };
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "ROUTE";
        }

        return switch (category.toUpperCase()) {
            case "STAY" -> "STAY";
            case "FOOD" -> "FOOD";
            case "TOUR" -> "TOUR";
            case "CAFE" -> "CAFE";
            case "ROUTE" -> "ROUTE";
            default -> "ROUTE";
        };
    }

    // 커뮤니티 - 게시글 작성
    @Transactional
    public Long createPost(Long userId, PostWriteDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));

        TravelPlan plan = null;

        if (dto.getPlanId() != null) {
            plan = travelPlanRepository.findById(dto.getPlanId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 여행 플랜을 찾을 수 없습니다."));
        }

        Post post = Post.builder()
                .user(user)
                .plan(plan)
                .title(dto.getTitle())
                .content(dto.getContent())
                .styleTags(dto.getStyleTags())
                .category(normalizeCategory(dto.getCategory()))
                .status("ACTIVE")
                .isPublic(dto.isPublic())
                .build();

        postRepository.save(post);

        if (dto.getImageUrls() != null) {
            for (int i = 0; i < dto.getImageUrls().size(); i++) {
                PostImage image = PostImage.builder()
                        .post(post)
                        .imageUrl(dto.getImageUrls().get(i))
                        .s3ObjectKey(dto.getImageUrls().get(i))
                        .imageOrder(i)
                        .build();
                postImageRepository.save(image);
            }
        }

        return post.getId();
    }

    // 커뮤니티 - 게시글 상세 조회
    @Transactional
    public PostDetailResponseDto getPost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        post.increaseViewCount();

        List<PostComment> comments = postCommentRepository
                .findByPostIdAndStatusOrderByCreatedAtAsc(postId, "ACTIVE", Pageable.unpaged())
                .getContent();

        List<PostImage> images = postImageRepository.findByPostIdOrderByImageOrderAsc(postId);

        boolean likedByMe = false;
        boolean scrappedByMe = false;

        if (userId != null) {
            likedByMe = postLikeRepository.existsByUserIdAndPostId(userId, postId);
            scrappedByMe = postScrapRepository.existsByUserIdAndPostId(userId, postId);
        }

        return PostDetailResponseDto.from(post, images, comments, likedByMe, scrappedByMe);
    }

    // 커뮤니티 - 게시글 삭제
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (post.getUser() == null || !Objects.equals(post.getUser().getId(), userId)) {
            throw new IllegalArgumentException("게시글 삭제 권한이 없습니다.");
        }

        List<PostImage> images = postImageRepository.findByPostId(postId);
        images.forEach(img -> localFileService.delete(img.getImageUrl()));
        postImageRepository.deleteAll(images);

        post.delete();
    }

    // 커뮤니티 - 게시글 수정
    @Transactional
    public Long updatePost(Long userId, Long postId, PostWriteDto dto) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (post.getUser() == null || !Objects.equals(post.getUser().getId(), userId)) {
            throw new IllegalArgumentException("본인이 작성한 게시글만 수정할 수 있습니다.");
        }

        post.update(
                dto.getTitle(),
                dto.getContent(),
                dto.getStyleTags(),
                normalizeCategory(dto.getCategory()),
                dto.isPublic()
        );

        // 수정 화면에서 새로 추가한 이미지가 있으면 기존 이미지 뒤에 추가
        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {
            List<PostImage> existingImages =
                    postImageRepository.findByPostIdOrderByImageOrderAsc(postId);

            int nextOrder = existingImages.stream()
                    .map(PostImage::getImageOrder)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(-1) + 1;

            for (int i = 0; i < dto.getImageUrls().size(); i++) {
                String imageUrl = dto.getImageUrls().get(i);

                PostImage image = PostImage.builder()
                        .post(post)
                        .imageUrl(imageUrl)
                        .s3ObjectKey(imageUrl)
                        .imageOrder(nextOrder + i)
                        .build();

                postImageRepository.save(image);
            }
        }

        return post.getId();
    }

    // 커뮤니티 - 댓글 작성
    @Transactional
    public Long addComment(Long userId, Long postId, PostWriteDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        PostComment comment = PostComment.builder()
                .post(post)
                .user(user)
                .content(dto.getContent())
                .status("ACTIVE")
                .build();

        return postCommentRepository.save(comment).getId();
    }

    // 커뮤니티 - 좋아요 등록
    @Transactional
    public void likePost(Long userId, Long postId) {
        if (postLikeRepository.existsByUserIdAndPostId(userId, postId)) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        PostLike postLike = PostLike.builder()
                .user(user)
                .post(post)
                .build();

        postLikeRepository.save(postLike);
        post.increaseLikeCount();
    }

    // 커뮤니티 - 좋아요 취소
    @Transactional
    public void unlikePost(Long userId, Long postId) {
        postLikeRepository.findByUserIdAndPostId(userId, postId)
                .ifPresent(postLike -> {
                    postLikeRepository.delete(postLike);
                    postLike.getPost().decreaseLikeCount();
                });
    }

    // 커뮤니티 - 좋아요 토글
    @Transactional
    public boolean toggleLikePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        return postLikeRepository.findByUserIdAndPostId(userId, postId)
                .map(postLike -> {
                    postLikeRepository.delete(postLike);
                    post.decreaseLikeCount();
                    return false; // 좋아요 취소됨
                })
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));

                    PostLike postLike = PostLike.builder()
                            .user(user)
                            .post(post)
                            .build();

                    postLikeRepository.save(postLike);
                    post.increaseLikeCount();
                    return true; // 좋아요 등록됨
                });
    }

    // 커뮤니티 - 게시글 스크랩 등록
    @Transactional
    public void scrapPost(Long userId, Long postId, String category) {
        if (postScrapRepository.existsByUserIdAndPostId(userId, postId)) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        String scrapCategory = (category == null || category.isBlank()) ? "ROUTE" : category;

        PostScrap postScrap = PostScrap.builder()
                .user(user)
                .post(post)
                .category(scrapCategory)
                .build();

        postScrapRepository.save(postScrap);
    }

    // 커뮤니티 - 게시글 스크랩 취소
    @Transactional
    public void cancelScrap(Long userId, Long postId) {
        postScrapRepository.findByUserIdAndPostId(userId, postId)
                .ifPresent(postScrapRepository::delete);
    }

    // 커뮤니티 - 스크랩 토글
    @Transactional
    public boolean toggleScrapPost(Long userId, Long postId, String category) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        return postScrapRepository.findByUserIdAndPostId(userId, postId)
                .map(postScrap -> {
                    postScrapRepository.delete(postScrap);
                    return false; // 스크랩 취소됨
                })
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));

                    String scrapCategory = (category == null || category.isBlank()) ? "ROUTE" : category;

                    PostScrap postScrap = PostScrap.builder()
                            .user(user)
                            .post(post)
                            .category(scrapCategory)
                            .build();

                    postScrapRepository.save(postScrap);
                    return true; // 스크랩 등록됨
                });
    }

    // 커뮤니티 - 댓글 목록 조회
    public List<PostDetailResponseDto.CommentInfo> getComments(Long postId) {
        return postCommentRepository
                .findByPostIdAndStatusOrderByCreatedAtAsc(postId, "ACTIVE", Pageable.unpaged())
                .getContent()
                .stream()
                .map(PostDetailResponseDto.CommentInfo::from)
                .collect(Collectors.toList());
    }

    // 장소 리뷰 저장 — 없는 장소는 자동 생성
    @Transactional
    public void savePlaceReviews(Long userId, Long postId, PlaceReviewSaveDto dto) {
        if (dto == null || dto.getReviews() == null || dto.getReviews().isEmpty()) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        dto.getReviews().forEach(item -> {
            if (item.getPlaceName() == null || item.getPlaceName().isBlank()) return;

            Place place = placeService.findOrCreatePlace(
                    item.getPlaceName().trim(),
                    item.getPlaceType() != null ? item.getPlaceType().trim() : "tour",
                    null, null);
            if (place == null) return; // 매핑 불가 타입이면 스킵

            placeReviewRepository.save(PlaceReview.builder()
                    .place(place)
                    .post(post)
                    .user(user)
                    .rating(Math.max(1, Math.min(5, item.getRating())))
                    .comment(item.getComment() != null ? item.getComment().trim() : null)
                    .build());
        });
    }

    // 장소 리뷰 수정 (별점·한줄평만 변경, 장소 연결은 유지)
    @Transactional
    public void updatePlaceReviews(Long userId, Long postId, PlaceReviewUpdateDto dto) {
        if (dto == null || dto.getReviews() == null || dto.getReviews().isEmpty()) return;

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        if (!post.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인이 작성한 게시글만 수정할 수 있습니다.");
        }

        dto.getReviews().forEach(item -> {
            if (item.getPlaceReviewId() == null || item.getRating() < 1) return;
            placeReviewRepository.findById(item.getPlaceReviewId()).ifPresent(pr -> {
                if (!pr.getPost().getId().equals(postId)) return; // 다른 게시글 리뷰 변조 방지
                pr.update(item.getRating(), item.getComment());
            });
        });
    }

    // 게시글별 장소 리뷰 목록 조회
    public List<PlaceReviewResponseDto> getPlaceReviewsByPost(Long postId) {
        return placeReviewRepository.findByPost_IdOrderByCreatedAtDesc(postId)
                .stream()
                .map(PlaceReviewResponseDto::from)
                .collect(Collectors.toList());
    }

    // 커뮤니티 - 게시글 이미지 삭제
    @Transactional
    public void deletePostImage(Long userId, Long postId, String imageUrl) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (post.getUser() == null || !Objects.equals(post.getUser().getId(), userId)) {
            throw new IllegalArgumentException("본인이 작성한 게시글의 이미지만 삭제할 수 있습니다.");
        }

        PostImage image = postImageRepository.findByPostIdOrderByImageOrderAsc(postId)
                .stream()
                .filter(img -> Objects.equals(img.getImageUrl(), imageUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다."));

        // 실제 로컬 이미지 파일 삭제
        localFileService.delete(image.getImageUrl());

        // DB에서 POST_IMAGES row 삭제
        postImageRepository.delete(image);
    }
}