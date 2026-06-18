package idusw.sbb.triplinker.domain.admin.dto;

public record SuspendRequestDto(
        String reason,
        String notifyMessage
) {}