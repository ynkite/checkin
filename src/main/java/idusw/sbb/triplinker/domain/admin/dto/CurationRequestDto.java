package idusw.sbb.triplinker.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CurationRequestDto(
        String title,
        String theme,
        Integer displayOrder,
        String startDate,
        String endDate,
        Long planId,
        Integer isDefault,
        String destination,
        @JsonProperty("extra_notes") String extraNotes
) {}