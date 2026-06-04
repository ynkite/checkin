package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

/**
 * UserService 인터페이스를 구현하여 회원 도메인의 실제 서비스 로직을 처리하는 구현체입니다.
 * 예외 처리(IllegalArgumentException) 및 데이터 트랜잭션 관리를 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService { // 인터페이스 구현

    private final UserRepository userRepository;
    private final UserSecurityHistoryRepository historyRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // 1. 회원 프로필 조회 로직
    @Override
    public UserInfoResponseDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        return new UserInfoResponseDto(user);
    }

    // 2. 회원 닉네임 변경 비즈니스 로직 (쓰기 작업이므로 @Transactional 명시)
    @Override
    @Transactional
    public void updateNickname(Long userId, UserNicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.updateNickname(request.getName()); // 엔티티 내부 변경 감지(Dirty Checking) 발동
    }

    // 3. 회원 논리 탈퇴 비즈니스 로직 (쓰기 작업이므로 @Transactional 명시)
    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.withdraw(); // 내부에서 DELETED로 변경됨
    }
    @Override
    public boolean verifyPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, String name, String region, String gender, LocalDate birthDate, String mbti) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        user.updateProfile(name, region, gender, birthDate, mbti);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String currentRaw, String newRaw) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        if (!passwordEncoder.matches(currentRaw, user.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        user.updatePassword(passwordEncoder.encode(newRaw));
    }

    @Override
    @Transactional
    public void loginFailed(String username, String ipAddress) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. Username: " + username));

        user.increaseLoginFailCount(); // User 엔티티 메서드 호출

        UserSecurityHistory history = UserSecurityHistory.of(
                user,
                SecurityEventType.LOGIN_FAIL,
                "로그인 실패 IP: " + ipAddress
        );
        historyRepository.save(history); // UserServiceImpl에서는 이 이름이 맞습니다.
    }
}