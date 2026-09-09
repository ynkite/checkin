package idusw.sbb.checkin.domain.tour.dto;

/**
 * 관광공사 5종 API 의 공통 정규화 모델.
 * 각 API 가 좌표·이름 필드명을 다르게 쓰므로 화면·엔진은 이 모델만 본다.
 */
public record Spot(
        String id,            // contentid 등 원본 식별자
        String name,
        double lat,           // mapy
        double lon,           // mapx
        String areaCode,
        String sigunguCode,
        Integer contentTypeId,
        String imageUrl,      // firstimage (없으면 null — 빈 문자열로 채우지 말 것)
        String address
) {}
