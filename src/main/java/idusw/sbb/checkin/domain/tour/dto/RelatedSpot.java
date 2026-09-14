package idusw.sbb.checkin.domain.tour.dto;

/**
 * TarRlteTarService1 연관 관광지 — TMAP 내비 이동 기록 기반 "실제 함께 방문된" 장소.
 * 좌표가 없다. 좌표가 필요하면 name 으로 KorService2 를 조회한다.
 */
public record RelatedSpot(
        String fromName,      // tAtsNm  기준 관광지
        String toName,        // rlteTatsNm  연관 장소
        String category,      // rlteCtgryLclsNm  관광지 / 음식 / 숙박
        int rank,             // rlteRank
        String regionName,    // rlteRegnNm
        String sigunguName    // rlteSignguNm
) {}
