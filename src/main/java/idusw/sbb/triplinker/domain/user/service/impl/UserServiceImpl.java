package idusw.sbb.triplinker.domain.user.service.impl;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserService 인터페이스를 구현하여 회원 도메인의 실제 서비스 로직을 처리하는 구현체입니다.
 * 예외 처리(IllegalArgumentException) 및 데이터 트랜잭션 관리를 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService { // 인터페이스 구현

    private final UserRepository userRepository;

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
        user.withdraw(); // 엔티티 내부에서 상태를 INACTIVE로 전환
    }
}