package idusw.sbb.triplinker.domain.system.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.system.dto.AdminReportListResponseDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRejectRequestDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRequestDto;
import idusw.sbb.triplinker.domain.system.service.SystemService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @PostMapping("/api/posts/{postId}/reports")
    public ResponseEntity<ApiResponse<Void>> submitReport(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ReportRequestDto dto) {
        systemService.submitReport(postId, userDetails.getUserId(), dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/api/admin/reports")
    public ResponseEntity<ApiResponse<Page<AdminReportListResponseDto>>> getReports(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(systemService.getReports(status, pageable)));
    }

    @DeleteMapping("/api/admin/reports/{reportId}")
    public ResponseEntity<ApiResponse<Void>> deleteReportedPost(
            @PathVariable Long reportId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails adminDetails) {
        systemService.deleteReportedPost(adminDetails.getUserId(), reportId, reason);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/api/admin/reports/{reportId}")
    public ResponseEntity<ApiResponse<Void>> rejectReport(
            @PathVariable Long reportId,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @RequestBody ReportRejectRequestDto dto) {
        systemService.rejectReport(adminDetails.getUserId(), reportId, dto);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}