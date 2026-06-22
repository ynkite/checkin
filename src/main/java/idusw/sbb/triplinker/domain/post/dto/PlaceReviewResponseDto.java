package idusw.sbb.triplinker.domain.post.dto;

import idusw.sbb.triplinker.domain.post.entity.PlaceReview;

import java.time.LocalDateTime;

public record PlaceReviewResponseDto(
        Long          id,
        Long          placeId,
        String        placeName,
        String        category,
        Long          writerId,
        String        writerName,
        String        writerRole,    // ADMIN / USER (현재 역할)
        int           rating,
        String        starsHtml,
        String        comment,
        Long          postId,
        String        postTitle,
        LocalDateTime createdAt
) {
    public static PlaceReviewResponseDto from(PlaceReview pr) {
        String writerName;
        try {
            writerName = "DELETED".equals(String.valueOf(pr.getUser().getStatus()))
                    ? "탈퇴한 사용자"
                    : pr.getUser().getName();
        } catch (Exception e) {
            writerName = "사용자";
        }

        String postTitle = (pr.getPost() != null) ? pr.getPost().getTitle() : null;

        return new PlaceReviewResponseDto(
                pr.getId(),
                pr.getPlace().getId(),
                pr.getPlace().getName(),
                pr.getPlace().getCategory().name().toLowerCase(),
                pr.getUser().getId(),
                writerName,
                pr.getUser().getRole(),
                pr.getRating(),
                stars(pr.getRating()),
                pr.getComment(),
                pr.getPost() != null ? pr.getPost().getId() : null,
                postTitle,
                pr.getCreatedAt()
        );
    }

    private static String stars(int rating) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) sb.append(i <= rating ? "★" : "☆");
        return sb.toString();
    }
}