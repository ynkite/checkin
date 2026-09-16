package idusw.sbb.checkin.domain.detection.dto;

import idusw.sbb.checkin.domain.detection.DetectionRules;

import java.util.List;

// 감지 결과 — 신호 목록 + 재계획 권장 여부(하나라도 걸리면 true).
// maskedLat/Lon 은 가명처리된 격자 좌표. 서버는 정밀 좌표를 갖지 않는다는 증빙(없으면 null).
public record DetectionResult(
        List<DetectionRules.Signal> signals,
        boolean replanRecommended,
        Double maskedLat,
        Double maskedLon
) {
    public static DetectionResult of(List<DetectionRules.Signal> signals, Double maskedLat, Double maskedLon) {
        boolean any = signals.stream().anyMatch(DetectionRules.Signal::triggered);
        return new DetectionResult(signals, any, maskedLat, maskedLon);
    }
}
