package idusw.sbb.triplinker.domain.system.entity;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 게시글 조회 로그(POST_VIEW_LOGS 테이블) 엔티티
// ⚠️ 테이블_정의서.csv / 클래스_정의서.csv에 없던 신규 테이블
// 기간별 조회수(visitCount)를 정확히 집계하기 위해 "조회가 발생한 시각"을 한 줄씩 쌓는다.
@Entity
@Table(name = "POST_VIEW_LOGS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id")
    private User viewer; // 비로그인 조회는 null 허용

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public PostViewLog(Post post, User viewer) {
        this.post = post;
        this.viewer = viewer;
    }
}