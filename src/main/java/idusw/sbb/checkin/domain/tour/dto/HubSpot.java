package idusw.sbb.checkin.domain.tour.dto;

/**
 * LocgoHubTarService1 기초지자체 중심(허브) 관광지 — 좌표 + 순위.
 * 밴드 B 허브 선정에 hubRank 와 좌표를 쓴다 (홍성찬 동선 엔진).
 * 좌표 필드가 mapX/mapY(대문자)라 KorService2 의 mapx/mapy 와 다르다.
 */
public record HubSpot(
        String name,          // hubTatsNm
        double lat,           // mapY
        double lon,           // mapX
        String areaCode,      // areaCd
        String sigunguCode,   // signguCd
        String category,      // hubCtgryLclsNm
        int rank              // hubRank  1 = 최상위 허브
) {}
