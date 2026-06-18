package idusw.sbb.triplinker.domain.user.controller;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetails;
import idusw.sbb.triplinker.domain.user.dto.ScrapResponseDto;
import idusw.sbb.triplinker.domain.user.service.UserService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PlaceScrapController {

    private final UserService userService;

    // 장소 스크랩 추가
    @PostMapping("/api/places/{placeId}/scraps")
    public ResponseEntity<ApiResponse<Void>> addScrap(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long placeId,
            @RequestBody Map<String, String> body
    ) {
        String category = toDbCategory(body.getOrDefault("category", "tour"));
        userService.addPlaceScrap(userDetails.getUserId(), placeId, category);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 장소 스크랩 삭제
    @DeleteMapping("/api/scraps/{scrapId}")
    public ResponseEntity<ApiResponse<Void>> deleteScrap(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long scrapId
    ) {
        userService.deletePlaceScrap(userDetails.getUserId(), scrapId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 내 장소 스크랩 목록 조회
    @GetMapping("/api/scraps")
    public ResponseEntity<ApiResponse<List<ScrapResponseDto>>> getMyScraps(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ScrapResponseDto> result = userService.getMyScrapsAll(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String toDbCategory(String type) {
        return switch (type.toLowerCase()) {
            case "accommodation", "stay" -> "STAY";
            case "restaurant", "food"   -> "FOOD";
            case "cafe"                  -> "CAFE";
            default                      -> "TOUR";
        };
    }
}