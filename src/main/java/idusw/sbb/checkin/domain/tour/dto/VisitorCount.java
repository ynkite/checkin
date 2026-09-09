package idusw.sbb.checkin.domain.tour.dto;

/**
 * DataLabService 지역별 방문자 수 — 시도 단위 일별 방문자(현지인/외지인).
 * 집중률의 평소 기준선(예측값과의 이탈 감지)으로 쓴다.
 */
public record VisitorCount(
        String date,          // baseYmd YYYYMMDD (과거 실측)
        String areaCode,      // areaCode
        String areaName,      // areaNm
        String dayOfWeek,     // daywkDivNm
        String visitorType,   // touDivNm  현지인(a) / 외지인(b)
        double count          // touNum
) {}
