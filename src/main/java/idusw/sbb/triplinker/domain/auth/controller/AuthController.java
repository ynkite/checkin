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

    // 이메일 인증 기능

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


}