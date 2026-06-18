package idusw.sbb.triplinker.domain.admin.controller;

import idusw.sbb.triplinker.domain.admin.dto.*;
import idusw.sbb.triplinker.domain.admin.service.AdminService;
import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
// 관리자 - 회원 관리 REST Controller
// SecurityConfig에서 "/api/admin/**"는 이미 ROLE_ADMIN만 접근 가능하도록 막혀 있다.

public class AdminController {

    private final AdminService adminService;

    // 대시보드 카드(총 가입자/일정/게시글/신고 미처리) - GET /api/admin/dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponseDto>> getDashboard() {
        log.info(" 대시보드 API 호출 성공");
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboard()));
    }

    // 기간별 통계 - GET /api/admin/statistics?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdminStatisticsResponseDto>> getStatistics(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getStatistics(startDate, endDate)));
    }

    // 회원 목록 조회 (상태 필터 + 페이징) - GET /api/admin/users?status=&page=&size=
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserListResponseDto>>> getUsers(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AdminUserListResponseDto> result = adminService.getUsers(status, pageable);
        log.info(" 회원 관리 API 호출 성공");
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 회원 정지 - PATCH /api/admin/users/{userId}/suspend
    @PatchMapping("/users/{userId}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendUser(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable Long userId,
            @RequestBody SuspendRequestDto dto) {

        adminService.suspendUser(adminDetails.getUserId(), userId, dto);
        log.info(" 신고 관리 API 호출 성공");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 회원 정지 해제 - PATCH /api/admin/users/{userId}/unsuspend
    @PatchMapping("/users/{userId}/unsuspend")
    public ResponseEntity<ApiResponse<Void>> unsuspendUser(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable Long userId) {

        adminService.unsuspendUser(adminDetails.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/users/{userId}/promote")
    public ResponseEntity<ApiResponse<Void>> promoteToAdmin(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable Long userId) {

        adminService.promoteToAdmin(adminDetails.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/users/{userId}/demote")
    public ResponseEntity<ApiResponse<Void>> demoteToUser(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable Long userId) {

        adminService.demoteToUser(adminDetails.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/curations")
    public ResponseEntity<ApiResponse<Page<CurationResponseDto>>> getCurations(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getCurations(pageable)));
    }

    @GetMapping("/curations/{curationId}")
    public ResponseEntity<ApiResponse<CurationResponseDto>> getCuration(@PathVariable Long curationId) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getCuration(curationId)));
    }

    @PostMapping("/curations")
    public ResponseEntity<ApiResponse<Long>> createCuration(
            @AuthenticationPrincipal CustomUserDetails adminDetails, @RequestBody CurationRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success(adminService.createCuration(adminDetails.getUserId(), dto)));
    }

    @PatchMapping("/curations/{curationId}")
    public ResponseEntity<ApiResponse<Void>> updateCuration(
            @AuthenticationPrincipal CustomUserDetails adminDetails, @PathVariable Long curationId,
            @RequestBody CurationRequestDto dto) {
        adminService.updateCuration(adminDetails.getUserId(), curationId, dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/curations/{curationId}")
    public ResponseEntity<ApiResponse<Void>> deleteCuration(
            @AuthenticationPrincipal CustomUserDetails adminDetails, @PathVariable Long curationId) {
        adminService.deleteCuration(adminDetails.getUserId(), curationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}