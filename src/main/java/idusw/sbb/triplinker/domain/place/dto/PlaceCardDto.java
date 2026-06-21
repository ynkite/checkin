package idusw.sbb.triplinker.domain.place.dto;

import idusw.sbb.triplinker.domain.place.entity.PlaceCategory;

public record PlaceCardDto(
        Long   placeId,
        String name,
        String category,
        Double avgRating,
        Long   reviewCount,
        Long   scrapCount
) {
    // 쿼리가 6번째 컬럼(row[5])으로 스크랩 수를 함께 반환하는 경우
    public static PlaceCardDto from(Object[] row) {
        PlaceCategory cat = (PlaceCategory) row[2];
        return new PlaceCardDto(
                (Long)   row[0],
                (String) row[1],
                cat.name().toLowerCase(),
                row[3] != null ? ((Number) row[3]).doubleValue() : 0.0,
                row[4] != null ? ((Number) row[4]).longValue()   : 0L,
                (row.length > 5 && row[5] != null) ? ((Number) row[5]).longValue() : 0L
        );
    }

    // 스크랩 수를 별도로 주입하는 경우 (구버전 호환)
    public static PlaceCardDto from(Object[] row, long scrapCount) {
        PlaceCategory cat = (PlaceCategory) row[2];
        return new PlaceCardDto(
                (Long)   row[0],
                (String) row[1],
                cat.name().toLowerCase(),
                row[3] != null ? ((Number) row[3]).doubleValue() : 0.0,
                row[4] != null ? ((Number) row[4]).longValue()   : 0L,
                scrapCount
        );
    }
}