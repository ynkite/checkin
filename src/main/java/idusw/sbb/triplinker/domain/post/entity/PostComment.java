package idusw.sbb.triplinker.domain.post.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "POST_COMMENTS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment {

    // 댓글 기본키
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 댓글이 달린 게시글
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 댓글 작성 회원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 댓글 내용
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 댓글 상태: ACTIVE / DELETED
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    // 작성 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 수정 일시
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PostComment(Post post, User user, String content, String status) {
        this.post = post;
        this.user = user;
        this.content = content;
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 댓글 내용 수정
    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    // 댓글 삭제 처리
    public void delete() {
        this.status = "DELETED";
        this.updatedAt = LocalDateTime.now();
    }
    public void hide() {
        this.status = "HIDDEN";
        this.updatedAt = LocalDateTime.now();
    }
}