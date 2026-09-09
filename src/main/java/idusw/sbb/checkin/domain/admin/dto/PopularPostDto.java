package idusw.sbb.checkin.domain.admin.dto;

public record PopularPostDto(Long postId, String title, int viewCount, int likeCount) {}