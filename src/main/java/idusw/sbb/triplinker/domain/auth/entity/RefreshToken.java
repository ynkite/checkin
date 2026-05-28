// 로그인 시 발급하는 Refresh Token을 DB에 저장하는 Entity
// 로그아웃·탈퇴 시 완전 삭제 (Hard Delete)

package idusw.sbb.triplinker.domain.auth.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "REFRESH_TOKENS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 토큰 원문이 아닌 해시값만 저장 (보안)
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static RefreshToken of(User user, String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = LocalDateTime.now().plusDays(7);
        return token;
    }
}