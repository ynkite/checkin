package idusw.sbb.triplinker.domain.auth.controller;

import idusw.sbb.triplinker.domain.auth.dto.SignUpRequestDTO;
import idusw.sbb.triplinker.domain.auth.service.AuthService;
import idusw.sbb.triplinker.domain.auth.service.EmailAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailAuthService emailAuthService;

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

    // 인증번호 발송 요청
    @PostMapping("/send-email")
    public ResponseEntity<String> sendEmail(@RequestParam String email) {
        emailAuthService.sendEmailAuthCode(email);
        return ResponseEntity.ok("인증번호가 이메일로 발송되었습니다. 3분 안에 입력해 주세요");
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

        emailAuthService.sendEmailAuthCode(email);
        return ResponseEntity.ok("인증번호가 이메일로 발송되었습니다. 3분 안에 입력해 주세요");
    }

    // 인증번호 검증 후 비밀번호 최종 변경
    @PatchMapping("/password/reset")
    public ResponseEntity<String> resetPassword(@RequestBody java.util.Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        String newPassword = request.get("newPassword");

        try {
            // 인증번호 확인
            boolean isVerified = emailAuthService.verifyEmailCode(email, code);

            if (isVerified) {
                // 인증 성공 시 실제 비밀번호 변경
                authService.updatePassword(email, newPassword);
                return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다. 변경된 비밀번호로 로그인해주세요!");
            }
            return ResponseEntity.badRequest().body("인증에 실패했습니다.");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


}