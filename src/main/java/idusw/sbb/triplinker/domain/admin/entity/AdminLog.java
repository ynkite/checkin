package idusw.sbb.triplinker.domain.admin.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 관리자 활동 로그(ADMIN_LOGS 테이블) 엔티티
// 회원 정지/해제, 게시글 삭제, 신고 반려, 큐레이션 등록 등 관리자의 모든 조치를
// 기록한다. 한 번 기록되면 수정/삭제하지 않는 불변(append-only) 로그다.

@Entity
@Table(name = "ADMIN_LOGS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType; // USER_SUSPEND, USER_UNSUSPEND, POST_DELETE, REPORT_REJECT, CURATION_CREATE 등

    @Column(name = "target_id")
    private Long targetId; // 조치 대상의 PK (userId, postId, curationId 등)

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public AdminLog(User admin, String actionType, Long targetId, String description) {
        this.admin = admin;
        this.actionType = actionType;
        this.targetId = targetId;
        this.description = description;
    }

    // Service 단에서 AdminLog.of(...) 형태로 가독성 있게 쓰기 위한 정적 팩토리
    public static AdminLog of(User admin, String actionType, Long targetId, String description) {
        return AdminLog.builder()
                .admin(admin)
                .actionType(actionType)
                .targetId(targetId)
                .description(description)
                .build();
    }
}