package idusw.sbb.triplinker.domain.admin.dto;

public record CurationRequestDto(
        String title,
        String theme,
        Integer displayOrder,
        String startAt,
        String endAt,
        Long planId,
        Integer isDefault
) {}