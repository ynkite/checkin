package idusw.sbb.triplinker.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import idusw.sbb.triplinker.domain.admin.entity.Curation;

import java.time.LocalDateTime;

public record CurationResponseDto(
        Long curationId,
        String title,
        String theme,
        int displayOrder,
        LocalDateTime startDate,
        LocalDateTime endDate,
        boolean isDefault,
        Long planId,
        String destination,
        @JsonProperty("extra_notes") String extraNotes
) {
    public static CurationResponseDto from(Curation c) {
        // destination: Curation 자체 필드 우선, 없으면 연결된 plan의 destination
        String dest = c.getDestination() != null ? c.getDestination()
                : (c.getPlan() != null ? c.getPlan().getDestination() : null);
        return new CurationResponseDto(
                c.getId(), c.getTitle(), c.getTheme(), c.getDisplayOrder(),
                c.getStartAt(), c.getEndAt(), c.isDefault(),
                c.getPlan() != null ? c.getPlan().getId() : null,
                dest,
                c.getExtraNotes()
        );
    }
}