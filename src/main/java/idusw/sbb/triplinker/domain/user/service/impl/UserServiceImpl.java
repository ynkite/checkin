package idusw.sbb.triplinker.domain.user.service.impl;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import idusw.sbb.triplinker.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService { // 인터페이스 구현

    private final UserRepository userRepository;
    private final UserSecurityHistoryRepository historyRepository; // 💡 이력 저장을 위해 의존성 추가 필요 (생성자 주입됨)
    @Override
    public UserInfoResponseDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        return new UserInfoResponseDto(user);
    }

    @Override
    @Transactional
    public void updateNickname(Long userId, UserNicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.updateNickname(request.getName());
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.withdraw(); // 내부에서 DELETED로 변경됨
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