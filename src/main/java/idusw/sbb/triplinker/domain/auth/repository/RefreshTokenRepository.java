// Refresh Token 조회·삭제용 Repository
package idusw.sbb.triplinker.domain.auth.repository;

import idusw.sbb.triplinker.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    // 토큰 재발급 시 해시값으로 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 로그아웃·탈퇴 시 해당 유저 토큰 전체 삭제
    void deleteByUserId(Long userId);
}