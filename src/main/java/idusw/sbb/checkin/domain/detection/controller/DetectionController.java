package idusw.sbb.checkin.domain.detection.controller;

import idusw.sbb.checkin.domain.detection.dto.DetectionContext;
import idusw.sbb.checkin.domain.detection.dto.DetectionResult;
import idusw.sbb.checkin.domain.detection.service.DetectionService;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/detection")
@RequiredArgsConstructor
public class DetectionController {

    private final DetectionService detectionService;

    // 다음 관광지 맥락으로 혼잡·날씨·동선 이탈을 감지해 재계획 필요 여부를 낸다.
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<DetectionResult>> check(@RequestBody DetectionContext context) {
        return ResponseEntity.ok(ApiResponse.success(detectionService.detect(context)));
    }
}
