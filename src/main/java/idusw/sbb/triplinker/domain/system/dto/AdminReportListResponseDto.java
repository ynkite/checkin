package idusw.sbb.triplinker.domain.system.dto;

import idusw.sbb.triplinker.domain.system.entity.Report;

import java.time.LocalDateTime;

public record AdminReportListResponseDto(
        Long reportId,
        Long postId,
        String postTitle,
        String reporterName,
        String reason,
        String status,
        LocalDateTime createdAt
) {
    public static AdminReportListResponseDto from(Report report) {
        return new AdminReportListResponseDto(
                report.getId(),
                report.getPost().getId(),
                report.getPost().getTitle(),
                report.getReporter().getName(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}