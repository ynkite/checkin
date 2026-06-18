package idusw.sbb.triplinker.domain.post.dto;

import idusw.sbb.triplinker.domain.post.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PostResponseDto {
    private Long id;
    private String title;
    private String writer; // 작성자 이름 또는 닉네임
    private int viewCount;
    private int likeCount;
    private LocalDateTime createdAt;
    private String category;

    // 엔티티를 DTO로 변환하는 메서드
    public static PostResponseDto from(Post post) {
        return PostResponseDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .writer(post.getUser().getName()) // 작성자 정보
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount()) // 엔티티에 해당 필드가 있는지 확인 필요
                .createdAt(post.getCreatedAt())
                .category(post.getCategory() != null ? post.getCategory() : "기타")
                .build();
    }
}

// 전체 코드 제미나이 추가