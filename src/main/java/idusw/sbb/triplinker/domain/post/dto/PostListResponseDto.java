package idusw.sbb.triplinker.domain.post.dto;

import idusw.sbb.triplinker.domain.post.entity.Post;

public record PostListResponseDto(
        Long postId,
        String catLabel,    //카테고리 라벨
        String catClass,    //cat-route, cat-food, cat-stay 등으로 나눠, 카테고리별로 색상을 입히기 위해 사용
        String title,
        int likes,
        int views
) {
    public static PostListResponseDto from(Post post) {
        return new PostListResponseDto(
                post.getId(),
                "카테고리 미정",
                "cat-route",
                post.getTitle(),
                post.getLikeCount(),
                post.getViewCount()
        );
    }
}