// Refresh Token 조회·삭제용 Repository
package idusw.sbb.triplinker.domain.auth.repository;

import idusw.sbb.triplinker.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    // 토큰 재발급 시 해시값으로 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 로그아웃·탈퇴 시 해당 유저 토큰 전체 삭제
    /*
     * [수정 내용]
     * 로그인 성공 시 기존 Refresh Token을 삭제하는 과정에서
     * "No EntityManager with actual transaction available" 오류가 발생하여
     * deleteByUserId() 메서드에 @Transactional을 명시함.
     *
     * delete 계열 메서드는 DB 데이터를 변경하는 작업이므로
     * 트랜잭션 범위 안에서 실행되어야 안정적으로 동작한다.
     */
    @Transactional
    void deleteByUserId(Long userId);
}