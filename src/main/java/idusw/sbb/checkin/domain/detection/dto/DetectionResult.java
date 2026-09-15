package idusw.sbb.checkin.domain.detection.dto;

import idusw.sbb.checkin.domain.detection.DetectionRules;

import java.util.List;

// 감지 결과 — 신호 목록 + 재계획 권장 여부(하나라도 걸리면 true).
public record DetectionResult(
        List<DetectionRules.Signal> signals,
        boolean replanRecommended
) {
    public static DetectionResult of(List<DetectionRules.Signal> signals) {
        boolean any = signals.stream().anyMatch(DetectionRules.Signal::triggered);
        return new DetectionResult(signals, any);
    }
}
