package idusw.sbb.triplinker.domain.post.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "POST_SCRAPS",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_post_scrap_user_post",
                        columnNames = {"user_id", "post_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostScrap {

    // 스크랩 기본키
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 스크랩한 회원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 스크랩 대상 게시글
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 스크랩 유형: ROUTE / ACCOMMODATION / RESTAURANT
    @Column(nullable = false, length = 20)
    private String category;

    // 스크랩 등록 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PostScrap(User user, Post post, String category) {
        this.user = user;
        this.post = post;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }
}