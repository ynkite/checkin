package idusw.sbb.triplinker.domain.admin.dto;

public record PopularPostDto(Long postId, String title, int viewCount, int likeCount) {}