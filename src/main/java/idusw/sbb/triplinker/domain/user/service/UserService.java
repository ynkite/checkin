package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import java.time.LocalDate;

/**
 * 회원 관리 기능의 비즈니스 핵심 명세를 정의한 서비스 인터페이스입니다.
 * 약속된 기능(조회, 수정, 탈퇴)을 명시하여 느슨한 결합 구조를 만듭니다.
 */
public interface UserService {
    UserInfoResponseDto getProfile(Long userId);
    void updateNickname(Long userId, UserNicknameUpdateRequest request);
    void withdraw(Long userId);
    void loginFailed(String username, String ipAddress);

    boolean verifyPassword(Long userId, String rawPassword);
    void updateProfile(Long userId, String name, String region, String gender, LocalDate birthDate, String mbti);
    void updatePassword(Long userId, String currentRaw, String newRaw);
}