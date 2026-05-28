// AuthService 인터페이스 - 구현체(AuthServiceImpl)와 분리
package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.dto.LoginRequestDto;
import idusw.sbb.triplinker.domain.auth.dto.TokenResponseDto;

public interface AuthService {

    // 로그인 → 계정 잠금 체크 → JWT 발급
    TokenResponseDto login(LoginRequestDto dto);

    // Refresh Token으로 Access Token 재발급
    TokenResponseDto refresh(String refreshToken);

    // 로그아웃 → Refresh Token 삭제
    void logout(Long userId);

    // 비밀번호 재설정 이메일 발송
    void sendPasswordResetEmail(String email);

    // 이메일 링크의 토큰으로 비밀번호 실제 변경
    void resetPassword(String token, String newPassword);
}