// 비밀번호 재설정 이메일 링크에 들어가는 토큰 Entity
// 30분 유효, 1회 사용 후 재사용 불가
package idusw.sbb.triplinker.domain.auth.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "PASSWORD_RESET_TOKENS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // DB에는 TINYINT로 저장됨
    @Column(name = "is_used", nullable = false)
    private boolean isUsed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static PasswordResetToken of(User user, String token) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.user = user;
        prt.token = token;
        prt.expiresAt = LocalDateTime.now().plusMinutes(30);
        return prt;
    }

    // 만료 안 됐고 아직 사용 안 한 토큰인지 확인
    public boolean isValid() {
        return !isUsed && LocalDateTime.now().isBefore(expiresAt);
    }

    // 비밀번호 변경 완료 후 호출해서 재사용 막기
    public void markUsed() {
        this.isUsed = true;
    }
}