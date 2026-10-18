// Refresh Token 조회·삭제용 Repository
package idusw.sbb.checkin.domain.auth.repository;

import idusw.sbb.checkin.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    // 토큰 재발급 시 해시값으로 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 로그인할 때 그 유저의 만료된 토큰만 정리 (다른 기기 토큰은 그대로)
    @Transactional
    void deleteByUserIdAndExpiresAtBefore(Long userId, java.time.LocalDateTime now);

    // 로그아웃: 이 기기 토큰 하나만
    @Transactional
    void deleteByUserIdAndTokenHash(Long userId, String tokenHash);

    // 탈퇴 시 해당 유저 토큰 전체 삭제
    @Transactional
    void deleteByUserId(Long userId);
}