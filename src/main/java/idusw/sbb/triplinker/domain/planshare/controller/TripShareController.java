package idusw.sbb.triplinker.domain.planshare.controller;

import idusw.sbb.triplinker.domain.planshare.dto.ShareInviteRequestDto;
import idusw.sbb.triplinker.domain.planshare.dto.TripMemberResponseDto;
import idusw.sbb.triplinker.domain.planshare.entity.PlanRole;
import idusw.sbb.triplinker.domain.planshare.service.TripShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips/{tripId}")
@RequiredArgsConstructor
public class TripShareController {

    private final TripShareService tripShareService;

    // 멤버 목록 조회
    @GetMapping("/members")
    public ResponseEntity<Map<String, Object>> getMembers(@PathVariable Long tripId) {
        List<TripMemberResponseDto> members = tripShareService.getMembers(tripId);
        return ResponseEntity.ok(Map.of("success", true, "data", members));
    }

    // 이메일 멤버 초대
    @PostMapping("/members")
    public ResponseEntity<Map<String, Object>> inviteMember(
            @PathVariable Long tripId,
            @RequestBody ShareInviteRequestDto requestDto) {
        try {
            tripShareService.inviteMember(tripId, requestDto);
            return ResponseEntity.ok(Map.of("success", true, "message", "초대가 완료되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // 공유 링크 생성 (role=READER|EDITOR, 기본 READER)
    @PostMapping("/share")
    public ResponseEntity<Map<String, Object>> createShareLink(
            @PathVariable Long tripId,
            @RequestParam(value = "role", defaultValue = "READER") PlanRole role) {
        Map<String, String> linkData = tripShareService.generateShareLink(tripId, role);
        return ResponseEntity.ok(Map.of("success", true, "data", linkData));
    }

    // 링크 재발급 — 기존 토큰을 끊고 새로 발급 (유출 대응)
    @PostMapping("/share/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateShareLink(
            @PathVariable Long tripId,
            @RequestParam(value = "role", defaultValue = "READER") PlanRole role) {
        Map<String, String> linkData = tripShareService.regenerateShareLink(tripId, role);
        return ResponseEntity.ok(Map.of("success", true, "data", linkData));
    }

    // 링크 폐기
    @DeleteMapping("/share")
    public ResponseEntity<Map<String, Object>> revokeShareLink(
            @PathVariable Long tripId,
            @RequestParam(value = "role", defaultValue = "READER") PlanRole role) {
        tripShareService.revokeShareLink(tripId, role);
        return ResponseEntity.ok(Map.of("success", true, "message", "링크가 폐기되었습니다."));
    }
}