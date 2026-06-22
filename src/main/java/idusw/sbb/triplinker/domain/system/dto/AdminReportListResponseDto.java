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
        LocalDateTime createdAt,
        Long commentId,
        String commentAuthorName,
        String postAuthorName,   // 게시글 작성자 이름 (테이블에 바로 표시용)
        String processedByName   // 처리한 관리자 이름
) {
    public static AdminReportListResponseDto from(Report report) {
        return from(report, null);
    }

    public static AdminReportListResponseDto from(Report report, String commentAuthorName) {
        // 게시글 작성자 이름
        String postAuthorName = null;
        try {
            postAuthorName = report.getPost() != null && report.getPost().getUser() != null
                    ? report.getPost().getUser().getName()
                    : null;
        } catch (Exception ignored) {}

        return new AdminReportListResponseDto(
                report.getId(),
                report.getPost().getId(),
                report.getPost().getTitle(),
                report.getReporter().getName(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getCommentId(),
                commentAuthorName,
                postAuthorName,
                report.getProcessedByName()
        );
    }
}