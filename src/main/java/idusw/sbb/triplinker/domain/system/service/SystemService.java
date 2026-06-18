package idusw.sbb.triplinker.domain.system.service;

import idusw.sbb.triplinker.domain.system.dto.AdminReportListResponseDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRejectRequestDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SystemService {

    void submitReport(Long postId, Long reporterId, ReportRequestDto dto);

    Page<AdminReportListResponseDto> getReports(String status, Pageable pageable);

    void deleteReportedPost(Long adminId, Long reportId, String reason);

    void rejectReport(Long adminId, Long reportId, ReportRejectRequestDto dto);
}