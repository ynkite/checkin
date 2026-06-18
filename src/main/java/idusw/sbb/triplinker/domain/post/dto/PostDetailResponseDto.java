package idusw.sbb.triplinker.domain.post.dto;

import idusw.sbb.triplinker.domain.post.entity.Post;

import java.time.LocalDateTime;

public record PostDetailResponseDto(
        Long postId,
        String title,
        String content,
        String authorName,
        String styleTags,
        int likeCount,
        int viewCount,
        LocalDateTime createdAt
) {
    public static PostDetailResponseDto from(Post post) {
        // 탈퇴 회원이 쓴 글이면 작성자명을 '탈퇴한 사용자'로 치환 (요구사항_정의서 규칙)
        String authorName = "DELETED".equals(post.getUser().getStatus())
                ? "탈퇴한 사용자"
                : post.getUser().getName();

        return new PostDetailResponseDto(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                authorName,
                post.getStyleTags(),
                post.getLikeCount(),
                post.getViewCount(),
                post.getCreatedAt()
        );
    }
}