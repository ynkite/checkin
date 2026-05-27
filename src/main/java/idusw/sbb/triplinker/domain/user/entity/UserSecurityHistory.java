package idusw.sbb.triplinker.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "USER_SECURITY_HISTORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSecurityHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SecurityEventType eventType;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 정적 팩토리 메서드
    public static UserSecurityHistory of(User user, SecurityEventType eventType, String description) {
        UserSecurityHistory history = new UserSecurityHistory();
        history.user = user;
        history.eventType = eventType;
        history.description = description;
        return history;
    }
}