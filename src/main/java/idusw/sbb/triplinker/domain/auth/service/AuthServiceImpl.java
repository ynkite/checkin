// 로그인 실패 횟수 관리, 계정 잠금, JWT 발급, 비밀번호 재설정 핵심 로직
package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.dto.LoginRequestDto;
import idusw.sbb.triplinker.domain.auth.dto.TokenResponseDto;
import idusw.sbb.triplinker.domain.auth.entity.PasswordResetToken;
import idusw.sbb.triplinker.domain.auth.entity.RefreshToken;
import idusw.sbb.triplinker.domain.auth.repository.PasswordResetTokenRepository;
import idusw.sbb.triplinker.domain.auth.repository.RefreshTokenRepository;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSecurityHistoryRepository userSecurityHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    // JWT 유틸은 팀에서 만든 JwtProvider 주입 (나중에 추가)
    // private final JwtProvider jwtProvider;

    @Override
    public TokenResponseDto login(LoginRequestDto dto) {
        // 1. 유저 조회 - 없으면 예외
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다."));

        // 2. 계정 잠금 여부 확인
        if (user.isLocked()) {
            throw new IllegalStateException("로그인 5회 실패로 계정이 잠겼습니다. 5분 후 다시 시도해주세요.");
        }

        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            user.increaseLoginFailCount(); // 실패 횟수 +1, 5회 시 자동 잠금
            userRepository.save(user);
            throw new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다.");
        }

        // 4. 로그인 성공 → 실패 횟수 초기화
        user.resetLoginFail();
        userRepository.save(user);

        // 5. JWT 발급 (JwtProvider 완성 후 아래 주석 해제)
        // String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        // String refreshTokenValue = jwtProvider.createRefreshToken();
        // String tokenHash = hashToken(refreshTokenValue);
        // refreshTokenRepository.save(RefreshToken.of(user, tokenHash));

        // 6. 90일 비밀번호 변경 권장 여부 체크
        boolean pwChangeRecommended = false;
        if (user.getLastPwChangedAt() != null) {
            long days = ChronoUnit.DAYS.between(user.getLastPwChangedAt(), LocalDateTime.now());
            pwChangeRecommended = days >= 90;
        }

        // TODO: JwtProvider 완성되면 실제 토큰값으로 교체
        return new TokenResponseDto("access-token-placeholder", "refresh-token-placeholder", pwChangeRecommended);
    }

    @Override
    public TokenResponseDto refresh(String refreshToken) {
        // 받은 토큰 해시화해서 DB 조회
        // String tokenHash = hashToken(refreshToken);
        RefreshToken saved = refreshTokenRepository.findByTokenHash(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        // 만료 확인
        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(saved);
            throw new IllegalStateException("토큰이 만료되었습니다. 다시 로그인해주세요.");
        }

        // 새 Access Token 발급
        // String newAccessToken = jwtProvider.createAccessToken(saved.getUser().getId(), saved.getUser().getRole());
        return new TokenResponseDto("new-access-token-placeholder", refreshToken, false);
    }

    @Override
    public void logout(Long userId) {
        // Refresh Token 완전 삭제 (Hard Delete)
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Override
    public void sendPasswordResetEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 가입된 계정이 없습니다."));

        // 소셜 전용 계정은 비밀번호 없음
        if (user.getPasswordHash() == null) {
            throw new IllegalStateException("소셜 계정은 비밀번호 재설정을 사용할 수 없습니다.");
        }

        // UUID로 토큰 생성 후 저장
        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.of(user, token));

        // TODO: 이메일 발송 로직 (EmailService 완성 후 추가)
        // emailService.sendResetEmail(email, token);
        System.out.println("[개발용] 재설정 링크: /reset-password?token=" + token);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        // 만료되거나 이미 사용한 토큰이면 거절
        if (!resetToken.isValid()) {
            throw new IllegalStateException("만료되었거나 이미 사용된 토큰입니다.");
        }

        // 비밀번호 변경 + 변경 시각 갱신
        User user = resetToken.getUser();
        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 토큰 사용 완료 처리
        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);

        // 보안 이력 기록
        userSecurityHistoryRepository.save(
                UserSecurityHistory.of(user, SecurityEventType.PW_CHANGE, "비밀번호 재설정 완료")
        );
    }
}