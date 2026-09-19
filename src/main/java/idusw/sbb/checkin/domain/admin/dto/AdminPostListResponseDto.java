package idusw.sbb.checkin.domain.admin.dto;

import idusw.sbb.checkin.domain.post.entity.Post;

import java.time.LocalDateTime;

// 관리자 - 게시글 목록 한 줄. 숨기기/다시 보이기 판단에 필요한 것만 담는다
public record AdminPostListResponseDto(
        Long postId,
        String title,
        String writerName,
        String category,
        String status,        // ACTIVE / HIDDEN
        int viewCount,
        LocalDateTime createdAt
) {
    public static AdminPostListResponseDto from(Post p) {
        return new AdminPostListResponseDto(
                p.getId(),
                p.getTitle(),
                p.getUser() != null ? p.getUser().getName() : null,
                p.getCategory(),
                p.getStatus(),
                p.getViewCount(),
                p.getCreatedAt());
    }
}
