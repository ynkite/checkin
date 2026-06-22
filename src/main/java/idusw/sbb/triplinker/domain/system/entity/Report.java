package idusw.sbb.triplinker.domain.system.entity;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 게시글 신고(REPORTS 테이블) 엔티티
// 사용자가 신고하면 PENDING 상태로 생성되고, 관리자가 삭제 처리(RESOLVED) 또는
// 반려 처리(REJECTED)하면 상태가 바뀐다.

@Entity
@Table(name = "REPORTS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "comment_id")
    private Long commentId;  // 댓글 신고 시 해당 댓글 ID (게시글 신고는 null)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, REJECTED, RESOLVED

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "processed_by_name", length = 100)
    private String processedByName;  // 처리한 관리자 이름

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "PENDING";
    }

    @Builder
    public Report(Post post, User reporter, String reason, Long commentId) {
        this.post = post;
        this.reporter = reporter;
        this.reason = reason;
        this.commentId = commentId;
        this.status = "PENDING";
    }

    // 관리자 - 신고 반려(무혐의) 처리
    public void reject(String adminNote, String processedByName) {
        this.status = "REJECTED";
        this.adminNote = adminNote;
        this.processedAt = LocalDateTime.now();
        this.processedByName = processedByName;
    }

    // 관리자 - 신고를 받아들여 게시글 삭제로 종결 처리
    public void resolve(String adminNote, String processedByName) {
        this.status = "RESOLVED";
        this.adminNote = adminNote;
        this.processedAt = LocalDateTime.now();
        this.processedByName = processedByName;
    }

}