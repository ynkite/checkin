package idusw.sbb.triplinker.domain.user.controller;

import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import idusw.sbb.triplinker.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 회원 정보 관리(조회, 수정, 탈퇴)를 위한 REST Controller입니다.
 * 클라이언트의 요청을 받아 유저 서비스 로직(UserService)으로 연결하는 진입점 역할을 합니다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 1. 회원 프로필 조회 (GET) - 특정 유저의 상세 정보를 반환합니다.
    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoResponseDto> getProfile(@PathVariable Long userId) { // 👈 반환 타입 변경!
        UserInfoResponseDto response = userService.getProfile(userId); // 👈 타입 변경!
        return ResponseEntity.ok(response);
    }

    // 2. 닉네임 변경 (PATCH) - 유저의 name(닉네임) 필드를 수정합니다
    @PatchMapping("/{userId}/nickname")
    public ResponseEntity<String> updateNickname(
            @PathVariable Long userId,
            @RequestBody UserNicknameUpdateRequest request) {
        userService.updateNickname(userId, request);
        return ResponseEntity.ok("닉네임이 성공적으로 변경되었습니다.");
    }

    // 3. 회원 탈퇴 (DELETE) - 유저의 계정 상태를 논리 삭제(INACTIVE) 처리합니다.
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> withdraw(@PathVariable Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.ok("회원 탈퇴가 완료되었습니다.");
    }
}