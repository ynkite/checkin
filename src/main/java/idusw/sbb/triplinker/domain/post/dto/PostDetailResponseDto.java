package idusw.sbb.triplinker.domain.post.dto;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.entity.PostComment;
import idusw.sbb.triplinker.domain.post.entity.PostImage;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponseDto(
        Long postId,
        Long userId,
        String writerName,

        String category,        //ROUTE, STAY, FOOD, TOUR, CAFE
        String catLabel,        //화면 표시용 카테고리 라벨
        String catClass,        //CSS 클래스

        Long planId,
        String planTitle,
        String planDestination,
        String planStartDate,
        String planEndDate,
        String planRouteJson,
        Integer planCompanionCount,
        String planTravelStyles,
        String planTransportType,
        Long planBudget,

        String title,
        String content,
        String styleTags,
        int likeCount,
        int viewCount,
        String status,
        boolean isPublic,
        boolean likedByMe,
        boolean scrappedByMe,
        List<String> imageUrls,
        List<CommentInfo> comments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostDetailResponseDto from(Post post,
                                             List<PostImage> images,
                                             List<PostComment> comments,
                                             boolean likedByMe,
                                             boolean scrappedByMe) {

        String writerName = "DELETED".equals(String.valueOf(post.getUser().getStatus()))
                ? "탈퇴한 사용자"
                : post.getUser().getName();

        String category = normalizeCategory(post.getCategory());

        return new PostDetailResponseDto(
                post.getId(),
                post.getUser().getId(),
                writerName,

                category,
                getCatLabel(category),
                getCatClass(category),

                post.getPlan() != null ? post.getPlan().getId() : null,
                post.getPlan() != null ? post.getPlan().getTitle() : null,
                post.getPlan() != null ? post.getPlan().getDestination() : null,
                post.getPlan() != null && post.getPlan().getStartDate() != null
                        ? post.getPlan().getStartDate().toString()
                        : null,
                post.getPlan() != null && post.getPlan().getEndDate() != null
                        ? post.getPlan().getEndDate().toString()
                        : null,
                post.getPlan() != null ? post.getPlan().getRouteJson() : null,
                post.getPlan() != null && post.getPlan().getForm() != null
                        ? post.getPlan().getForm().getCompanionCount()
                        : null,
                post.getPlan() != null && post.getPlan().getForm() != null
                        ? post.getPlan().getForm().getTravelStyles()
                        : null,
                post.getPlan() != null && post.getPlan().getForm() != null
                        ? post.getPlan().getForm().getTransportType()
                        : null,
                post.getPlan() != null && post.getPlan().getForm() != null
                        ? post.getPlan().getForm().getBudget()
                        : null,

                post.getTitle(),
                post.getContent(),
                post.getStyleTags(),
                post.getLikeCount(),
                post.getViewCount(),
                post.getStatus(),
                post.isPublic(),
                likedByMe,
                scrappedByMe,
                images.stream().map(PostImage::getImageUrl).toList(),
                comments.stream().map(CommentInfo::from).toList(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "ROUTE";
        }

        return switch (category) {
            case "STAY" -> "STAY";
            case "FOOD" -> "FOOD";
            case "TOUR" -> "TOUR";
            case "CAFE" -> "CAFE";
            case "ROUTE" -> "ROUTE";
            default -> "ROUTE";
        };
    }

    private static String getCatLabel(String category) {
        return switch (category) {
            case "STAY" -> "숙소";
            case "FOOD" -> "맛집";
            case "TOUR" -> "관광지";
            case "CAFE" -> "카페";
            default -> "여행 경로";
        };
    }

    private static String getCatClass(String category) {
        return switch (category) {
            case "STAY" -> "cat-stay";
            case "FOOD" -> "cat-food";
            case "TOUR" -> "cat-tour";
            case "CAFE" -> "cat-cafe";
            default -> "cat-route";
        };
    }

    // 댓글 응답 정보
    public record CommentInfo(
            Long commentId,          // 댓글 ID
            Long userId,             // 댓글 작성자 ID
            String writerName,       // 댓글 작성자명
            String content,          // 댓글 내용
            String status,           // 댓글 상태
            LocalDateTime createdAt, // 작성 일시
            LocalDateTime updatedAt  // 수정 일시
    ) {
        public static CommentInfo from(PostComment comment) {
            String writerName = "DELETED".equals(String.valueOf(comment.getUser().getStatus()))
                    ? "탈퇴한 사용자"
                    : comment.getUser().getName();

            return new CommentInfo(
                    comment.getId(),
                    comment.getUser().getId(),
                    writerName,
                    comment.getContent(),
                    comment.getStatus(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt()
            );
        }
    }
}