package idusw.sbb.checkin.domain.push.entity;

import idusw.sbb.checkin.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 브라우저 푸시 구독 정보. 한 사용자가 기기·브라우저마다 여러 구독을 가질 수 있다(endpoint 단위).
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uq_push_endpoint", columnNames = {"endpoint"})
)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 푸시 서비스 엔드포인트 URL (기기·브라우저마다 고유)
    @Column(nullable = false, length = 500)
    private String endpoint;

    // 페이로드 암호화용 공개키(p256dh)와 인증 시크릿(auth) — 브라우저가 구독 시 넘겨준다
    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = LocalDateTime.now();
    }
}
