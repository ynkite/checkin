package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.dto.SignUpRequestDTO;

public interface AuthService {

    // 닉네임(아이디) 중복 체크
    boolean checkUsername(String username);

    // 이메일 중복 체크
    boolean checkEmail(String email);

    // 회원가입
    void signUp(SignUpRequestDTO dto);

    // 비밀번호 변경
    void updatePassword(String email, String newPassword);
}