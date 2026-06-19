package idusw.sbb.triplinker.domain.admin.entity;

import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 관리자 큐레이션(CURATIONS 테이블) 엔티티
// 메인 화면 상단에 노출되는 시즌·테마별 추천 여행 경로를 관리한다.

@Entity
@Table(name = "CURATIONS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private TravelPlan plan; // 추천 경로로 연결할 플랜 (없을 수도 있음)

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String theme;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "destination", length = 100)
    private String destination;

    @Column(name = "extra_notes", columnDefinition = "TEXT")
    private String extraNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public Curation(User admin, TravelPlan plan, String title, String theme, int displayOrder,
                    LocalDateTime startAt, LocalDateTime endAt, boolean isDefault,
                    String destination, String extraNotes) {
        this.admin = admin;
        this.plan = plan;
        this.title = title;
        this.theme = theme;
        this.displayOrder = displayOrder;
        this.startAt = startAt;
        this.endAt = endAt;
        this.isDefault = isDefault;
        this.destination = destination;
        this.extraNotes = extraNotes;
    }

    // 큐레이션 수정 (null/blank인 필드는 기존 값 유지)
    public void update(TravelPlan plan, String title, String theme, Integer displayOrder,
                       LocalDateTime startAt, LocalDateTime endAt, String destination, String extraNotes) {
        if (plan != null) this.plan = plan;
        if (title != null && !title.isBlank()) this.title = title;
        if (theme != null) this.theme = theme;
        if (displayOrder != null) this.displayOrder = displayOrder;
        this.startAt = startAt;
        this.endAt = endAt;
        if (destination != null) this.destination = destination;
        if (extraNotes != null) this.extraNotes = extraNotes;
    }
}