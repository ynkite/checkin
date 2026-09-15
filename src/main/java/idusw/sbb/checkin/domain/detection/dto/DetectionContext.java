package idusw.sbb.checkin.domain.detection.dto;

import java.time.LocalDate;

// 감지 입력 — 다음 관광지 맥락. routeDelayRatio 는 외부(동선 소스)에서 주입, 없으면 null.
public record DetectionContext(
        String region,          // 날씨 조회용 (예: 부산)
        String areaCd,          // 집중률 조회용 지역코드
        String signguCd,        // 시군구코드
        String nextPlaceName,   // 다음 관광지 이름 (집중률 매칭)
        LocalDate travelDate,   // 방문 날짜
        Double routeDelayRatio  // 실측/예측 이동시간 비율 (없으면 null)
) {}
