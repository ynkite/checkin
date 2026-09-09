package idusw.sbb.checkin.domain.admin.dto;

public record SuspendRequestDto(
        String reason,
        String notifyMessage
) {}