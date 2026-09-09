package idusw.sbb.checkin.domain.system.dto;

public record ReportRejectRequestDto(String status, String reason, String adminNote, String notifyMessage) {}