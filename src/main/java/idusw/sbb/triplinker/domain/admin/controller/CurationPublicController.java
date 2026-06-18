package idusw.sbb.triplinker.domain.admin.controller;

import idusw.sbb.triplinker.domain.admin.dto.CurationResponseDto;
import idusw.sbb.triplinker.domain.admin.service.AdminService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/curations")
@RequiredArgsConstructor
public class CurationPublicController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CurationResponseDto>>> getPublicCurations() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getPublicCurations()));
    }
}