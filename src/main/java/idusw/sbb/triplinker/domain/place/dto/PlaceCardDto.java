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