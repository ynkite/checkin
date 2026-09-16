package idusw.sbb.checkin.domain.tour.dto;

/**
 * TatsCnctrRateService 관광지 집중률 예측 — 관광지·날짜별 혼잡도 예측값.
 * 좌표 없음. 장소 매칭은 placeName(이름) 으로 KorService2 와 조인한다.
 */
public record ConcentrationRate(
        String date,          // baseYmd YYYYMMDD (미래 날짜)
        String placeName,     // tAtsNm
        String areaName,      // areaNm
        String sigunguName,   // signguNm
        double rate           // cnctrRate  클수록 붐빔
) {}
