// 로그인 실패 횟수 관리, 계정 잠금, JWT 발급, 비밀번호 재설정 핵심 로직
package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.dto.LoginRequestDto;
import idusw.sbb.triplinker.domain.auth.dto.TokenResponseDto;
import idusw.sbb.triplinker.domain.auth.entity.PasswordResetToken;
import idusw.sbb.triplinker.domain.auth.entity.RefreshToken;
import idusw.sbb.triplinker.domain.auth.repository.OAuthAccountRepository;
import idusw.sbb.triplinker.domain.auth.repository.PasswordResetTokenRepository;
import idusw.sbb.triplinker.domain.auth.repository.RefreshTokenRepository;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.auth.dto.SignUpRequestDTO;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import idusw.sbb.triplinker.global.exception.LoginFailException;
import idusw.sbb.triplinker.global.exception.SocialAccountExistException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final OAuthAccountRepository oAuthAccountRepository;

    @Override
    @Transactional(noRollbackFor = LoginFailException.class)
    public TokenResponseDto login(LoginRequestDto request) {

        // 유저 조회
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 계정 잠금 상태 확인
        if (user.isLocked()) {
            long remainSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), user.getLockedUntil());
            throw new LoginFailException(true, user.getLoginFailCount(), Math.max(remainSeconds, 0));
        }

        // 비밀번호 검증(로그인 실패 시 카운트 증가)
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.increaseLoginFailCount();
            userRepository.save(user);
            if (user.isLocked()) {
                long remainSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), user.getLockedUntil());
                throw new LoginFailException(true, user.getLoginFailCount(), Math.max(remainSeconds, 0));
            }
            throw new LoginFailException(false, user.getLoginFailCount(), 0);
        }

        // 로그인 성공 시 실패 카운트 초기화
        user.resetLoginFail();
        userRepository.save(user);

        // 토큰 발급
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // Refresh Token 해싱 후 DB 저장 (기존 토큰 지우고 새로 저장)
        refreshTokenRepository.deleteByUserId(user.getId());
        String hashedRefreshToken = hashSha256(refreshToken);
        RefreshToken tokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashedRefreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(tokenEntity);

        // 90일 비밀번호 변경 권장 체크
        boolean pwChangeRecommended = false;
        if (user.getLastPwChangedAt() != null) {
            long days = ChronoUnit.DAYS.between(user.getLastPwChangedAt(), LocalDateTime.now());
            pwChangeRecommended = days >= 90;
        }

        // 최종 응답 반환
        return new TokenResponseDto(accessToken, refreshToken, pwChangeRecommended);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public TokenResponseDto refresh(String refreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(saved);
            throw new IllegalStateException("토큰이 만료되었습니다. 다시 로그인해주세요.");
        }

        return new TokenResponseDto("new-access-token-placeholder", refreshToken, false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Override
    public void signUp(SignUpRequestDTO dto) {
        //최종 중복 검증
        if (checkUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        //이메일 중복 검증
        if (checkEmail(dto.getEmail())) {
            User existingUser = userRepository.findByEmail(dto.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("사용자 조회 실패"));

            oAuthAccountRepository.findByUserId(existingUser.getId())
                    .ifPresentOrElse(
                            oAuthAccount -> {
                                //소셜 계정 전용 예외(이메일 중복)
                                throw new SocialAccountExistException(oAuthAccount.getProvider());
                            },
                            () -> {
                                //일반 중복 에러
                                throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
                            }
                    );
            return;
        }

        //DTO -> Entity 변환
        User user = User.builder()
                .username(dto.getUsername())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getName())
                .email(dto.getEmail())
                .region(dto.getRegion())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .mbti(dto.getMbti())
                .build();

        //DB 저장
        userRepository.save(user);
    }

    @Override
    public void sendPasswordResetEmail(String email) {
        System.out.println("[개발용] sendPasswordResetEmail 임시 처리");
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (!resetToken.isValid()) {
            throw new IllegalStateException("만료되었거나 이미 사용된 토큰입니다.");
        }

        User user = resetToken.getUser();
        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);

        userSecurityHistoryRepository.save(
                UserSecurityHistory.of(user, SecurityEventType.PW_CHANGE, "비밀번호 재설정 완료")
        );
    }

    @Override
    public void updatePassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        user.updatePassword(passwordEncoder.encode(newPassword));
    }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("토큰 해싱 중 오류가 발생했습니다.", e);
        }
    }
}