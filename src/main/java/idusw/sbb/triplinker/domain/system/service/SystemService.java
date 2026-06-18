package idusw.sbb.triplinker.domain.system.service;

import idusw.sbb.triplinker.domain.system.dto.AdminReportListResponseDto;
import idusw.sbb.triplinker.domain.system.dto.NotificationResponseDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRejectRequestDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SystemService {

    void submitReport(Long postId, Long reporterId, ReportRequestDto dto);

    Page<AdminReportListResponseDto> getReports(String status, Pageable pageable);

    void deleteReportedPost(Long adminId, Long reportId, String reason);

    void rejectReport(Long adminId, Long reportId, ReportRejectRequestDto dto);

    // 게시글 신고
    Long reportPost(Long userId, ReportRequestDto dto);

    // 내 알림 목록 조회
    Page<NotificationResponseDto> getNotifications(Long userId, Pageable pageable);

    // 알림 읽음 처리
    void readNotification(Long userId, Long notificationId);
}