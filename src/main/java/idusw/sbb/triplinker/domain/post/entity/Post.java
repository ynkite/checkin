package idusw.sbb.triplinker.domain.post.entity;

import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "POSTS")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private TravelPlan plan;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "style_tags")
    private String styleTags;

    @Column(nullable = false, length = 20)
    private String category = "ROUTE";

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder

    public Post(User user, TravelPlan plan, String title, String content, String styleTags, String category, String status, boolean isPublic) {
        this.user = user;
        this.plan = plan;
        this.title = title;
        this.content = content;
        this.styleTags = styleTags;
        this.category = category != null ? category : "ROUTE";
        this.status = status != null ? status : "ACTIVE";
        this.isPublic = isPublic;
    }

    public void increaseLikeCount() {this.likeCount++;}
    public void decreaseLikeCount() {if(this.likeCount > 0) this.likeCount--;}
    public void increaseViewCount() {this.viewCount++;}
    public void delete() {this.status = "DELETED";}
    public void hide()   {this.status = "HIDDEN";}

    public void update(String title, String content, String styleTags, String category, Boolean isPublic) {
        this.title = title;
        this.content = content;
        this.styleTags = styleTags;
        this.category = category != null ? category : "ROUTE";

        if (isPublic != null) {
            this.isPublic = isPublic;
        }
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (this.createdAt == null) {
            this.createdAt = now;
        }

        if (this.updatedAt == null) {
            this.updatedAt = now;
        }

        if (this.status == null) {
            this.status = "ACTIVE";
        }

        if (this.category == null) {
            this.category = "ROUTE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}