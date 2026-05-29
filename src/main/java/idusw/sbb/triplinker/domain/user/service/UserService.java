package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;

/**
 * 회원 관리 기능의 비즈니스 핵심 명세를 정의한 서비스 인터페이스입니다.
 * 약속된 기능(조회, 수정, 탈퇴)을 명시하여 느슨한 결합 구조를 만듭니다.
 */
public interface UserService {
    // 회원 정보 프로필 조회 명세
    UserInfoResponseDto getProfile(Long userId);
    // 회원 닉네임 수정 명세
    void updateNickname(Long userId, UserNicknameUpdateRequest request);
    // 회원 논리 탈퇴 명세
    void withdraw(Long userId);

    // 💡 로그인 실패 로직 추가
    void loginFailed(String username, String ipAddress);
}