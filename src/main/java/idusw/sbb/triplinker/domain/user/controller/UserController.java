package idusw.sbb.triplinker.domain.user.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import idusw.sbb.triplinker.domain.user.service.UserService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 회원 정보 관리(조회, 수정, 탈퇴)를 위한 REST Controller입니다.
 * 클라이언트의 요청을 받아 유저 서비스 로직(UserService)으로 연결하는 진입점 역할을 합니다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 1-1. 내 프로필 조회 (GET) - JWT에서 추출한 현재 로그인 유저 정보를 반환합니다.
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponseDto>> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserInfoResponseDto response = userService.getProfile(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("조회 성공", response));
    }

    // 1-2. 회원 프로필 조회 (GET) - 특정 유저의 상세 정보를 반환합니다.
    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoResponseDto> getProfile(@PathVariable Long userId) {
        UserInfoResponseDto response = userService.getProfile(userId);
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

    // 4. 현재 비밀번호 검증 (POST /api/users/me/verify-password)
    @PostMapping("/me/verify-password")
    public ResponseEntity<ApiResponse<Void>> verifyPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        boolean ok = userService.verifyPassword(userDetails.getUser().getId(), body.get("password"));
        if (!ok) return ResponseEntity.badRequest().body(ApiResponse.error("비밀번호가 올바르지 않습니다."));
        return ResponseEntity.ok(ApiResponse.success("확인되었습니다.", null));
    }

    // 5. 내 프로필 수정 (PATCH /api/users/me)
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        String name      = body.get("name");
        String region    = body.get("region");
        String gender    = body.get("gender");
        String mbti      = body.get("mbti");
        String birthStr  = body.get("birthDate");
        LocalDate birthDate = (birthStr != null && !birthStr.isBlank()) ? LocalDate.parse(birthStr) : null;

        userService.updateProfile(userDetails.getUser().getId(), name, region, gender, birthDate, mbti);
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", null));
    }

    // 6. 비밀번호 변경 (PATCH /api/users/me/password)
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> updateMyPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        try {
            userService.updatePassword(
                    userDetails.getUser().getId(),
                    body.get("currentPassword"),
                    body.get("newPassword"));
            return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다.", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}