package idusw.sbb.triplinker.domain.user.controller;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto; // 👈 바뀐 DTO 이름으로 변경!
import idusw.sbb.triplinker.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 1. 프로필 조회 (GET)
    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoResponseDto> getProfile(@PathVariable Long userId) { // 👈 반환 타입 변경!
        UserInfoResponseDto response = userService.getProfile(userId); // 👈 타입 변경!
        return ResponseEntity.ok(response);
    }

    // 2. 닉네임 변경 (PATCH)
    @PatchMapping("/{userId}/nickname")
    public ResponseEntity<String> updateNickname(
            @PathVariable Long userId,
            @RequestBody UserNicknameUpdateRequest request) {
        userService.updateNickname(userId, request);
        return ResponseEntity.ok("닉네임이 성공적으로 변경되었습니다.");
    }

    // 3. 회원 탈퇴 (DELETE)
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> withdraw(@PathVariable Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.ok("회원 탈퇴가 완료되었습니다.");
    }
}