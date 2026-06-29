package idusw.sbb.triplinker.domain.system.service;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.entity.PostComment;
import idusw.sbb.triplinker.domain.post.repository.PostCommentRepository;
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
import java.util.List;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemServiceImpl implements SystemService {

    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
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

        boolean isComment = dto.commentId() != null && dto.commentId() > 0;

        // 중복 신고 방지
        if (reportRepository.existsDuplicateReport(userId, dto.postId(), dto.commentId())) {
            throw new IllegalArgumentException(isComment ? "이미 신고한 댓글입니다." : "이미 신고한 게시글입니다.");
        }

        Report report = Report.builder()
                .post(post)
                .reporter(reporter)
                .reason(dto.reason())
                .commentId(dto.commentId())
                .build();

        Long reportId = reportRepository.save(report).getId();

        // 신고당한 계정(피신고자)에게 알림 전송
        if (isComment) {
            PostComment comment = postCommentRepository.findById(dto.commentId())
                    .orElseThrow(() -> new IllegalArgumentException("신고 대상 댓글을 찾을 수 없습니다."));
            notificationService.send(
                    comment.getUser().getId(),
                    "COMMENT_REPORTED",
                    "댓글 신고 접수 안내",
                    "작성하신 댓글이 신고되었습니다. 사유: " + dto.reason()
            );
        } else {
            notificationService.send(
                    post.getUser().getId(),
                    "POST_REPORTED",
                    "게시글 신고 접수 안내",
                    "작성하신 게시글이 신고되었습니다. 사유: " + dto.reason()
            );
        }

        return reportId;
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

        boolean isComment = dto.commentId() != null && dto.commentId() > 0;

        // 중복 신고 방지
        if (reportRepository.existsDuplicateReport(reporterId, postId, dto.commentId())) {
            throw new IllegalArgumentException(isComment ? "이미 신고한 댓글입니다." : "이미 신고한 게시글입니다.");
        }

        reportRepository.save(Report.builder()
                .post(post)
                .reporter(reporter)
                .reason(dto.reason())
                .commentId(dto.commentId())
                .build());

        // 신고당한 계정(피신고자)에게 알림 전송
        if (isComment) {
            PostComment comment = postCommentRepository.findById(dto.commentId())
                    .orElseThrow(() -> new IllegalArgumentException("신고 대상 댓글을 찾을 수 없습니다."));
            notificationService.send(
                    comment.getUser().getId(),
                    "COMMENT_REPORTED",
                    "댓글 신고 접수 안내",
                    "작성하신 댓글이 신고되었습니다. 사유: " + dto.reason()
            );
        } else {
            notificationService.send(
                    post.getUser().getId(),
                    "POST_REPORTED",
                    "게시글 신고 접수 안내",
                    "작성하신 게시글이 신고되었습니다. 사유: " + dto.reason()
            );
        }
    }

    @Override
    public Page<AdminReportListResponseDto> getReports(String status, Pageable pageable) {
        Page<Report> reports = (status != null && !status.isBlank())
                ? reportRepository.findByStatusOrderByIdDesc(status, pageable)
                : reportRepository.findAll(pageable);
        return reports.map(report -> {
            String commentAuthorName = null;
            if (report.getCommentId() != null && report.getCommentId() > 0) {
                commentAuthorName = postCommentRepository.findById(report.getCommentId())
                        .map(c -> c.getUser().getName())
                        .orElse(null);
            }
            return AdminReportListResponseDto.from(report, commentAuthorName);
        });
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

        User admin = userRepository.findById(adminId).orElse(null);
        String adminName = (admin != null) ? admin.getName() : "관리자";

        Post post = report.getPost();
        post.delete();

        // 게시글에 달린 모든 PENDING 신고 일괄 처리
        List<Report> pendingReports = reportRepository.findByPostIdAndCommentIdIsNullAndStatus(post.getId(), "PENDING");
        for (Report r : pendingReports) {
            r.resolve(adminNote, adminName);
        }

        notificationService.send(post.getUser().getId(), "POST_DELETED", "게시글 삭제 안내", adminNote);
    }

    @Override
    @Transactional
    public void hideReportedContent(Long adminId, Long reportId, String reason) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다."));

        if (!"PENDING".equals(report.getStatus())) {
            throw new IllegalStateException("이미 처리된 신고입니다.");
        }

        String adminNote = (reason != null && !reason.isBlank())
                ? reason
                : "운영 정책 위반으로 해당 콘텐츠가 숨김 처리되었습니다.";

        User admin = userRepository.findById(adminId).orElse(null);
        String adminName = (admin != null) ? admin.getName() : "관리자";

        boolean isComment = report.getCommentId() != null && report.getCommentId() > 0;

        if (isComment) {
            // 댓글 숨김 처리
            postCommentRepository.findById(report.getCommentId()).ifPresent(comment -> {
                comment.hide();
                notificationService.send(comment.getUser().getId(),
                        "COMMENT_HIDDEN", "댓글 숨김 안내", adminNote);
            });
        } else if (report.getReason() != null && report.getReason().contains("[댓글 신고]")) {
            // 댓글 신고인데 commentId가 없음 → 게시글 숨김 금지, 오류 반환
            throw new IllegalArgumentException("댓글 신고이지만 댓글 ID가 없습니다. 댓글을 직접 확인해주세요.");
        } else {
            // 게시글 숨김 처리
            Post post = report.getPost();
            post.hide();
            notificationService.send(post.getUser().getId(),
                    "POST_HIDDEN", "게시글 숨김 안내", adminNote);
        }

        // 해당 글/댓글에 달린 모든 PENDING 신고 일괄 처리
        List<Report> pendingReports;
        if (isComment) {
            pendingReports = reportRepository.findByPostIdAndCommentIdAndStatus(
                    report.getPost().getId(), report.getCommentId(), "PENDING");
        } else {
            pendingReports = reportRepository.findByPostIdAndCommentIdIsNullAndStatus(
                    report.getPost().getId(), "PENDING");
        }
        for (Report r : pendingReports) {
            r.resolve(adminNote, adminName);
        }
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

        String baseMessage = (dto.notifyMessage() != null && !dto.notifyMessage().isBlank())
                ? dto.notifyMessage()
                : "귀하의 게시글에 대한 신고가 검토 후 반려되었습니다.";

        User admin = userRepository.findById(adminId).orElse(null);
        String adminName = (admin != null) ? admin.getName() : "관리자";
        report.reject(combinedNote, adminName);

        String notifContent = baseMessage
                + "\n신고사유 - " + report.getReason()
                + "\n반려사유 - " + dto.reason();

        notificationService.send(
                report.getReporter().getId(),
                "REPORT_REJECTED",
                "신고 반려 안내",
                notifContent
        );
    }
}