package idusw.sbb.triplinker.domain.planshare.controller;

import idusw.sbb.triplinker.domain.planshare.dto.ShareInviteRequestDto;
import idusw.sbb.triplinker.domain.planshare.dto.TripMemberResponseDto;
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

    // 읽기 전용 공유 링크 생성
    @PostMapping("/share")
    public ResponseEntity<Map<String, Object>> createShareLink(@PathVariable Long tripId) {
        Map<String, String> linkData = tripShareService.generateShareLink(tripId);
        return ResponseEntity.ok(Map.of("success", true, "data", linkData));
    }
}