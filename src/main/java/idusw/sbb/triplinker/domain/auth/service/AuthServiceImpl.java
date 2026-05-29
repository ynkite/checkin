// 로그인 실패 횟수 관리, 계정 잠금, JWT 발급, 비밀번호 재설정 핵심 로직
package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.dto.LoginRequestDto;
import idusw.sbb.triplinker.domain.auth.dto.TokenResponseDto;
import idusw.sbb.triplinker.domain.auth.entity.PasswordResetToken;
import idusw.sbb.triplinker.domain.auth.entity.RefreshToken;
import idusw.sbb.triplinker.domain.auth.repository.PasswordResetTokenRepository;
import idusw.sbb.triplinker.domain.auth.repository.RefreshTokenRepository;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.auth.dto.SignUpRequestDTO;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import lombok.RequiredArgsConstructor;
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
    // private final PasswordEncoder passwordEncoder; // 임시 주석 - SecurityConfig 완성 후 해제

    @Override
    public TokenResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다."));

        if (user.isLocked()) {
            throw new IllegalStateException("로그인 5회 실패로 계정이 잠겼습니다. 5분 후 다시 시도해주세요.");
        }

        // 임시 주석 - SecurityConfig 완성 후 해제
        // if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
        //     user.increaseLoginFailCount();
        //     userRepository.save(user);
        //     throw new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다.");
        // }

        user.resetLoginFail();
        userRepository.save(user);

        boolean pwChangeRecommended = false;
        if (user.getLastPwChangedAt() != null) {
            long days = ChronoUnit.DAYS.between(user.getLastPwChangedAt(), LocalDateTime.now());
            pwChangeRecommended = days >= 90;
        }

        return new TokenResponseDto("access-token-placeholder", "refresh-token-placeholder", pwChangeRecommended);
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
    @Transactional(readOnly = true)
    public boolean checkEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    @Transactional
    public void signUp(SignUpRequestDTO dto) {
        // 최종 중복 검증
        if (checkUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (checkEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // DTO -> Entity 변환
        User user = User.builder()
                .username(dto.getUsername())
                .passwordHash(dto.getPassword())
                .name(dto.getName())
                .email(dto.getEmail())
                .region(dto.getRegion())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .mbti(dto.getMbti())
                .build();

        // DB 저장
        userRepository.save(user);
    }

    @Override
    public void sendPasswordResetEmail(String email) {
        // 임시 주석 - findByEmail UserRepository에 추가 후 해제
        // User user = userRepository.findByEmail(email)
        //         .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 가입된 계정이 없습니다."));
        // if (user.getPasswordHash() == null) {
        //     throw new IllegalStateException("소셜 계정은 비밀번호 재설정을 사용할 수 없습니다.");
        // }
        // String token = UUID.randomUUID().toString();
        // passwordResetTokenRepository.save(PasswordResetToken.of(user, token));
        System.out.println("[개발용] sendPasswordResetEmail 임시 처리");
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));
    @Transactional
    public void updatePassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (!resetToken.isValid()) {
            throw new IllegalStateException("만료되었거나 이미 사용된 토큰입니다.");
        }

        User user = resetToken.getUser();
        // 임시 주석 - SecurityConfig 완성 후 해제
        // user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);

        userSecurityHistoryRepository.save(
                UserSecurityHistory.of(user, SecurityEventType.PW_CHANGE, "비밀번호 재설정 완료")
        );
    }

        // User 엔티티의 비밀번호 변경 메서드 호출
        user.updatePassword(newPassword);
    }
}