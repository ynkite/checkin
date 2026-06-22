package idusw.sbb.triplinker.domain.post.dto;

import idusw.sbb.triplinker.domain.post.entity.Post;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record PostListResponseDto(
        Long postId,
        String category,
        String catLabel,
        String catClass,
        String title,
        String content,
        String writerName,
        LocalDateTime createdAt,
        List<String> styleTags,
        int likes,
        int scraps,
        int views,
        String thumbnailUrl,         // 첫 번째 이미지 URL (없으면 null)
        boolean hidden,              // HIDDEN 상태 여부
        String writerRole,           // ADMIN / USER
        Long writerId                // 게시글 작성자 ID (본인 숨김 글 접근용)
) {
    public static PostListResponseDto from(Post post) {
        return from(post, 0, null);
    }

    public static PostListResponseDto from(Post post, int scraps) {
        return from(post, scraps, null);
    }

    public static PostListResponseDto from(Post post, int scraps, String thumbnailUrl) {
        String category = normalizeCategory(post.getCategory());

        return new PostListResponseDto(
                post.getId(),
                category,
                getCatLabel(category),
                getCatClass(category),
                post.getTitle(),
                post.getContent(),
                post.getUser() != null ? post.getUser().getName() : "사용자",
                post.getCreatedAt(),
                parseStyleTags(post.getStyleTags()),
                post.getLikeCount(),
                scraps,
                post.getViewCount(),
                thumbnailUrl,
                "HIDDEN".equals(post.getStatus()),
                post.getUser() != null ? post.getUser().getRole() : "USER",
                post.getUser() != null ? post.getUser().getId() : null
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

    private static List<String> parseStyleTags(String styleTags) {
        if (styleTags == null || styleTags.isBlank()) {
            return List.of();
        }

        String normalized = styleTags
                .trim()
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "");

        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .map(tag -> tag.replace("#", ""))
                .filter(tag -> !tag.isBlank())
                .toList();
    }
}