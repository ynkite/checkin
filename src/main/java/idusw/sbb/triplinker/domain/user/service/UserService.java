package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;

public interface UserService {
    UserInfoResponseDto getProfile(Long userId);
    void updateNickname(Long userId, UserNicknameUpdateRequest request);
    void withdraw(Long userId);

    // 💡 로그인 실패 로직 추가
    void loginFailed(String username, String ipAddress);
}