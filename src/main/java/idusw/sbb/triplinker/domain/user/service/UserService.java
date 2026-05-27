package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto; // DTO 이름 변경 반영

public interface UserService {
    UserInfoResponseDto getProfile(Long userId);
    void updateNickname(Long userId, UserNicknameUpdateRequest request);
    void withdraw(Long userId);
}