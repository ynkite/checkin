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

    // 제미나이 추가
    @Column(name = "category")
    private String category; // 필드 추가
    // 여기까지

    @Builder

    public Post(User user, TravelPlan plan, String title, String content, String styleTags, String status, boolean isPublic) {
        this.user = user;
        this.plan = plan;
        this.title = title;
        this.content = content;
        this.styleTags = styleTags;
        this.status = status != null ? status : "ACTIVE";
        this.isPublic = isPublic;
        this.category = category;     // 제미나이 추가 이 줄만
    }

    public void increaseLikeCount() {this.likeCount++;}
    public void decreaseLikeCount() {if(this.likeCount > 0) this.likeCount--;}
    public void increaseViewCount() {this.viewCount++;}
    public void delete() {this.status = "DELETED";}

}
