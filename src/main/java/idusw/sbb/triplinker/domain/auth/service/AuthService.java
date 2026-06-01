package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.dto.LoginRequestDto;
import idusw.sbb.triplinker.domain.auth.dto.SignUpRequestDTO;
import idusw.sbb.triplinker.domain.auth.dto.TokenResponseDto;

public interface AuthService {

    //로그인 → 계정 잠금 체크 → JWT 발급
    TokenResponseDto login(LoginRequestDto dto);
    //닉네임(아이디) 중복 체크
    boolean checkUsername(String username);

    //Refresh Token으로 Access Token 재발급
    TokenResponseDto refresh(String refreshToken);
    //이메일 중복 체크
    boolean checkEmail(String email);

    //로그아웃 → Refresh Token 삭제
    void logout(Long userId);
    //회원가입
    void signUp(SignUpRequestDTO dto);

    //비밀번호 변경
    void updatePassword(String email, String newPassword);
    //비밀번호 재설정 이메일 발송
    void sendPasswordResetEmail(String email);

    //이메일 링크의 토큰으로 비밀번호 실제 변경
    void resetPassword(String token, String newPassword);
}