// 비밀번호 재설정 토큰 조회용 Repository
package idusw.sbb.triplinker.domain.auth.repository;

import idusw.sbb.triplinker.domain.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // 이메일 링크의 토큰값으로 조회
    Optional<PasswordResetToken> findByToken(String token);
}