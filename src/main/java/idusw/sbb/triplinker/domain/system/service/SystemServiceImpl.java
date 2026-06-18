package idusw.sbb.triplinker.domain.system.service;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.repository.PostRepository;
import idusw.sbb.triplinker.domain.system.dto.AdminReportListResponseDto;
import idusw.sbb.triplinker.domain.system.dto.NotificationResponseDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRejectRequestDto;
import idusw.sbb.triplinker.domain.system.dto.ReportRequestDto;
import idusw.sbb.triplinker.domain.system.entity.Notification;
import idusw.sbb.triplinker.domain.system.entity.Report;
import idusw.sbb.triplinker.domain.system.repository.NotificationRepository;
import idusw.sbb.triplinker.domain.system.repository.ReportRepository;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemServiceImpl implements SystemService {

    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 게시글 신고 접수
    @Override
    @Transactional
    public Long reportPost(Long userId, ReportRequestDto dto) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("신고 회원을 찾을 수 없습니다."));

        Post post = postRepository.findById(dto.postId())
                .orElseThrow(() -> new IllegalArgumentException("신고 대상 게시글을 찾을 수 없습니다."));

        Report report = Report.builder()
                .post(post)
                .reporter(reporter)
                .reason(dto.reason())
                .build();

        return reportRepository.save(report).getId();
    }

    // 내 알림 목록 조회
    @Override
    public Page<NotificationResponseDto> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponseDto::from);
    }

    // 알림 읽음 처리
    @Override
    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!Objects.equals(notification.getUser().getId(), userId)) {
            throw new IllegalArgumentException("알림 읽음 처리 권한이 없습니다.");
        }

        notification.markRead();
    }

    @Override
    @Transactional
    public void submitReport(Long postId, Long reporterId, ReportRequestDto dto) {
        if (dto.reason() == null || dto.reason().isBlank()) {
            throw new IllegalArgumentException("신고 사유를 입력해주세요.");
        }

        Post post = postRepository.findById(postId)
                .filter(p -> !"DELETED".equals(p.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 게시글입니다."));

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        reportRepository.save(Report.builder()
                .post(post)
                .reporter(reporter)
                .reason(dto.reason())
                .build());
    }

    @Override
    public Page<AdminReportListResponseDto> getReports(String status, Pageable pageable) {
        Page<Report> reports = (status != null && !status.isBlank())
                ? reportRepository.findByStatusOrderByIdDesc(status, pageable)
                : reportRepository.findAll(pageable);
        return reports.map(AdminReportListResponseDto::from);
    }

    @Override
    @Transactional
    public void deleteReportedPost(Long adminId, Long reportId, String reason) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다."));

        if (!"PENDING".equals(report.getStatus())) {
            throw new IllegalStateException("이미 처리된 신고입니다.");
        }

        String adminNote = (reason != null && !reason.isBlank())
                ? reason
                : "관리자 판단에 따라 게시글이 삭제되었습니다.";

        Post post = report.getPost();
        post.delete();
        report.resolve(adminNote);

        notificationService.send(post.getUser().getId(), "POST_DELETED", "게시글 삭제 안내", adminNote);
    }

    @Override
    @Transactional
    public void rejectReport(Long adminId, Long reportId, ReportRejectRequestDto dto) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다."));

        if (!"PENDING".equals(report.getStatus())) {
            throw new IllegalStateException("이미 처리된 신고입니다.");
        }

        String combinedNote = dto.reason()
                + (dto.adminNote() != null && !dto.adminNote().isBlank() ? " - " + dto.adminNote() : "");

        report.reject(combinedNote);

        notificationService.send(
                report.getReporter().getId(),
                "REPORT_REJECTED",
                "신고 반려 안내",
                "신고하신 내용이 검토 후 반려되었습니다. (" + combinedNote + ")"
        );
    }
}