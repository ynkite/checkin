// 로그인·로그아웃·비밀번호 재설정 API 엔드포인트
package idusw.sbb.triplinker.domain.auth.controller;

import idusw.sbb.triplinker.domain.auth.dto.LoginRequestDto;
import idusw.sbb.triplinker.domain.auth.dto.TokenResponseDto;
import idusw.sbb.triplinker.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody LoginRequestDto dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    // POST /api/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refresh(body.get("refreshToken")));
    }

    // POST /api/auth/logout  (userId는 나중에 JWT에서 꺼낼 예정)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam Long userId) {
        authService.logout(userId);
        return ResponseEntity.ok().build();
    }

    // POST /api/auth/password/reset-request
    @PostMapping("/password/reset-request")
    public ResponseEntity<Void> sendResetEmail(@RequestBody Map<String, String> body) {
        authService.sendPasswordResetEmail(body.get("email"));
        return ResponseEntity.ok().build();
    }

    // PATCH /api/auth/password/reset
    @PatchMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("token"), body.get("newPassword"));
        return ResponseEntity.ok().build();
    }
}