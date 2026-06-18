package idusw.sbb.triplinker.domain.admin.dto;

import idusw.sbb.triplinker.domain.admin.entity.Curation;

import java.time.LocalDateTime;

public record CurationResponseDto(
        Long curationId,
        String title,
        String theme,
        int displayOrder,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean isDefault,
        Long planId
) {
    public static CurationResponseDto from(Curation c) {
        return new CurationResponseDto(
                c.getId(), c.getTitle(), c.getTheme(), c.getDisplayOrder(),
                c.getStartAt(), c.getEndAt(), c.isDefault(),
                c.getPlan() != null ? c.getPlan().getId() : null
        );
    }
}