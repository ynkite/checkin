// 로그인·로그아웃·비밀번호 재설정 API 엔드포인트
package idusw.sbb.triplinker.domain.auth.controller;

import idusw.sbb.triplinker.domain.auth.dto.PasswordResetDTO;
import idusw.sbb.triplinker.domain.auth.dto.SignUpRequestDTO;
import idusw.sbb.triplinker.domain.auth.dto.LoginRequestDto;
import idusw.sbb.triplinker.domain.auth.dto.TokenResponseDto;
import idusw.sbb.triplinker.domain.auth.service.AuthService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import idusw.sbb.triplinker.domain.auth.service.EmailAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailAuthService emailAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponseDto>> login(@RequestBody LoginRequestDto request) {

        TokenResponseDto tokenData = authService.login(request);

        return ResponseEntity.ok(
                ApiResponse.success("로그인 성공", tokenData)
        );
    }

    // 아이디 중복 체크 API
    @GetMapping("/check-username")
    public ResponseEntity<Boolean> checkUsername(@RequestParam String username) {
        return ResponseEntity.ok(authService.checkUsername(username));

    }

    // 이메일 중복 체크 API
    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(authService.checkEmail(email));
    }

    // 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@Valid @RequestBody SignUpRequestDTO dto) {
        authService.signUp(dto);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    // POST /api/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refresh(body.get("refreshToken")));
    }

    // 인증번호 발송 요청
    @PostMapping("/send-email")
    public ResponseEntity<String> sendEmail(@RequestParam String email) {
        emailAuthService.sendEmailAuthCode(email, "signup");
        return ResponseEntity.ok("인증번호가 이메일로 발송되었습니다. 3분 안에 입력해 주세요");
    }
    // POST /api/auth/logout  (userId는 나중에 JWT에서 꺼낼 예정)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam Long userId) {
        authService.logout(userId);
        return ResponseEntity.ok().build();
    }

    //인증번호 확인 요청
    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String email, @RequestParam String code) {
        try {
            // 사용자가 입력한 코드가 맞는지 확인
            boolean isVerified = emailAuthService.verifyEmailCode(email, code);
            return ResponseEntity.ok("이메일 인증이 완료되었습니다!");

        } catch (IllegalArgumentException e) {
            // 시간 초과나 번호가 틀렸을 경우 에러 메시지
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 비밀번호 재설정용 인증번호 발송
    @PostMapping("/password/reset-request")
    public ResponseEntity<String> resetPasswordRequest(@RequestBody java.util.Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("이메일을 입력해주세요.");
        }

        // 가입되지 않은 이메일이면 인증 메일을 보내지 않음
        if (!authService.checkEmail(email)) {
            return ResponseEntity.badRequest().body("TripLinker에 가입되지 않은 이메일입니다.");
        }

        emailAuthService.sendEmailAuthCode(email, "reset");
        return ResponseEntity.ok("인증번호가 이메일로 발송되었습니다. 3분 안에 입력해 주세요");
    }

    // 인증번호 검증 후 비밀번호 최종 변경
    @PatchMapping("/password/reset")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid PasswordResetDTO dto) {
        try {
            // 인증번호 맞는지 확인
            boolean isVerified = emailAuthService.verifyEmailCode(dto.getEmail(), dto.getCode());

            if (isVerified) {
                // 정규식 검사를 통과시킨 안전한 비밀번호만 이쪽으로 넘어옵니다.
                authService.updatePassword(dto.getEmail(), dto.getNewPassword());
                return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
            }
            return ResponseEntity.badRequest().body("인증번호가 틀렸거나 만료되었습니다.");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }


}