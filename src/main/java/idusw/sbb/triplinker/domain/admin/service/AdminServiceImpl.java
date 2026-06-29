package idusw.sbb.triplinker.domain.admin.service;

import idusw.sbb.triplinker.domain.admin.dto.*;
import idusw.sbb.triplinker.domain.admin.entity.AdminLog;
import idusw.sbb.triplinker.domain.admin.entity.Curation;
import idusw.sbb.triplinker.domain.admin.repository.AdminLogRepository;
import idusw.sbb.triplinker.domain.admin.repository.CurationRepository;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.post.repository.PostRepository;
import idusw.sbb.triplinker.domain.system.repository.PostViewLogRepository;
import idusw.sbb.triplinker.domain.system.repository.ReportRepository;
import idusw.sbb.triplinker.domain.system.service.NotificationService;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final UserSecurityHistoryRepository userSecurityHistoryRepository;
    private final AdminLogRepository adminLogRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final PostViewLogRepository postViewLogRepository;
    private final CurationRepository curationRepository;
    private final NotificationService notificationService;

    @Override
    public Page<AdminUserListResponseDto> getUsers(String status, Pageable pageable) {
        Page<User> users = (status != null && !status.isBlank())
                ? userRepository.findByStatus(status, pageable)
                : userRepository.findAll(pageable);
        return users.map(AdminUserListResponseDto::from);
    }

    @Override
    @Transactional
    public void suspendUser(Long adminId, Long targetUserId, SuspendRequestDto dto) {
        if (dto.reason() == null || dto.reason().isBlank()) {
            throw new IllegalArgumentException("정지 사유를 입력해주세요.");
        }
        User admin  = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        target.suspend();
        userSecurityHistoryRepository.save(
                UserSecurityHistory.of(target, SecurityEventType.ACCOUNT_SUSPENDED, dto.reason())
        );

        String notifyMessage = (dto.notifyMessage() != null && !dto.notifyMessage().isBlank())
                ? dto.notifyMessage()
                : "귀하의 계정은 운영 정책 위반으로 인해 정지되었습니다.";
        String notifContent = notifyMessage + "\n정지사유 - " + dto.reason();
        notificationService.send(target.getId(), "ACCOUNT_SUSPENDED", "계정 정지 안내", notifContent);

        adminLogRepository.save(AdminLog.of(admin, "USER_SUSPEND", target.getId(), dto.reason()));
    }

    @Override
    @Transactional
    public void unsuspendUser(Long adminId, Long targetUserId) {
        User admin  = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        target.unsuspend();
        userSecurityHistoryRepository.save(
                UserSecurityHistory.of(target, SecurityEventType.ACCOUNT_UNSUSPENDED, "관리자에 의한 정지 해제")
        );
        adminLogRepository.save(AdminLog.of(admin, "USER_UNSUSPEND", target.getId(), "정지 해제 처리"));
    }

    @Override
    @Transactional
    public void promoteToAdmin(Long adminId, Long targetUserId) {
        User admin  = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        if (!"ACTIVE".equals(target.getStatus())) {
            throw new IllegalArgumentException("활성 상태의 회원만 관리자로 지정할 수 있습니다.");
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new IllegalArgumentException("이미 관리자인 회원입니다.");
        }

        target.promoteToAdmin();
        notificationService.send(target.getId(), "ROLE_CHANGED", "관리자 권한 부여", "관리자 권한이 부여되었습니다.");
        adminLogRepository.save(AdminLog.of(admin, "USER_PROMOTE", target.getId(), "관리자 권한 부여"));
    }

    @Override
    @Transactional
    public void demoteToUser(Long adminId, Long targetUserId) {
        if (adminId.equals(targetUserId)) {
            throw new IllegalStateException("본인 계정의 관리자 권한은 해제할 수 없습니다.");
        }
        User admin  = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        if (!"ADMIN".equals(target.getRole())) {
            throw new IllegalArgumentException("이미 일반 회원입니다.");
        }

        target.demoteToUser();
        notificationService.send(target.getId(), "ROLE_CHANGED", "관리자 권한 해제", "관리자 권한이 해제되었습니다.");
        adminLogRepository.save(AdminLog.of(admin, "USER_DEMOTE", target.getId(), "관리자 권한 해제"));
    }

    @Override
    public AdminDashboardResponseDto getDashboard() {
        long totalUsers     = userRepository.count();
        long totalTrips     = travelPlanRepository.count();
        long totalPosts     = postRepository.countByStatus("ACTIVE");
        long pendingReports = reportRepository.countByStatus("PENDING");
        long totalPostViews = postRepository.sumViewCountByStatus("ACTIVE");

        StatusBreakdownDto userStatusBreakdown = new StatusBreakdownDto(
                userRepository.countByStatus("ACTIVE"),
                userRepository.countByStatus("SUSPENDED"),
                userRepository.countByStatus("DELETED")
        );
        ReportStatusBreakdownDto reportStatusBreakdown = new ReportStatusBreakdownDto(
                reportRepository.countByStatus("PENDING"),
                reportRepository.countByStatus("REJECTED"),
                reportRepository.countByStatus("RESOLVED")
        );
        List<PopularPostDto> popularPosts = postRepository.findTop5ByStatusOrderByViewCountDesc("ACTIVE")
                .stream()
                .map(p -> new PopularPostDto(p.getId(), p.getTitle(), p.getViewCount(), p.getLikeCount()))
                .toList();

        return new AdminDashboardResponseDto(
                totalUsers, totalTrips, totalPosts, pendingReports, totalPostViews,
                userStatusBreakdown, reportStatusBreakdown, popularPosts
        );
    }

    @Override
    public AdminStatisticsResponseDto getStatistics(String startDate, String endDate) {
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate);
            end   = LocalDate.parse(endDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)");
        }
        if (end.isBefore(start)) throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
        if (start.plusDays(90).isBefore(end)) throw new IllegalArgumentException("조회 기간은 최대 90일까지 가능합니다.");

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime   = end.atTime(23, 59, 59);

        long newUsers   = userRepository.countByCreatedAtBetween(startDateTime, endDateTime);
        long newTrips   = travelPlanRepository.countByCreatedAtBetween(startDateTime, endDateTime);
        long visitCount = postViewLogRepository.countByCreatedAtBetween(startDateTime, endDateTime);

        List<DestinationStatDto> popularDestinations = travelPlanRepository
                .findTopDestinations(startDateTime, endDateTime, PageRequest.of(0, 5))
                .stream()
                .map(row -> new DestinationStatDto((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        List<DailyTrendDto> dailyTrend = buildDailyTrend(
                start, end,
                userRepository.countDailyNewUsers(startDateTime, endDateTime),
                travelPlanRepository.countDailyNewTrips(startDateTime, endDateTime),
                postViewLogRepository.countDailyViews(startDateTime, endDateTime)
        );

        return new AdminStatisticsResponseDto(visitCount, newUsers, newTrips, popularDestinations, dailyTrend);
    }

    @Override
    public Page<CurationResponseDto> getCurations(Pageable pageable) {
        return curationRepository.findAllByOrderByDisplayOrderAsc(pageable).map(CurationResponseDto::from);
    }

    @Override
    public CurationResponseDto getCuration(Long curationId) {
        Curation curation = curationRepository.findById(curationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 큐레이션입니다."));
        return CurationResponseDto.from(curation);
    }

    @Override
    @Transactional
    public Long createCuration(Long adminId, CurationRequestDto dto) {
        if (dto.title() == null || dto.title().isBlank()) {
            throw new IllegalArgumentException("큐레이션 제목을 입력해주세요.");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));
        TravelPlan plan = (dto.planId() != null) ? travelPlanRepository.findById(dto.planId()).orElse(null) : null;

        Curation curation = Curation.builder()
                .admin(admin).plan(plan).title(dto.title()).theme(dto.theme())
                .displayOrder(dto.displayOrder())
                .startAt(parseDate(dto.startDate())).endAt(parseDate(dto.endDate()))
                .isDefault(dto.isDefault() != null && dto.isDefault() == 1)
                .destination(dto.destination())
                .extraNotes(dto.extraNotes())
                .build();

        Long curationId = curationRepository.save(curation).getId();
        adminLogRepository.save(AdminLog.of(admin, "CURATION_CREATE", curationId, dto.title()));
        return curationId;
    }

    @Override
    @Transactional
    public void updateCuration(Long adminId, Long curationId, CurationRequestDto dto) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));
        Curation curation = curationRepository.findById(curationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 큐레이션입니다."));
        TravelPlan plan = (dto.planId() != null) ? travelPlanRepository.findById(dto.planId()).orElse(null) : null;

        curation.update(plan, dto.title(), dto.theme(), dto.displayOrder(),
                parseDate(dto.startDate()), parseDate(dto.endDate()),
                dto.isDefault() != null && dto.isDefault() == 1,
                dto.destination(), dto.extraNotes());
        adminLogRepository.save(AdminLog.of(admin, "CURATION_UPDATE", curationId, dto.title()));
    }

    @Override
    @Transactional
    public void deleteCuration(Long adminId, Long curationId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));
        Curation curation = curationRepository.findById(curationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 큐레이션입니다."));

        curationRepository.delete(curation);
        adminLogRepository.save(AdminLog.of(admin, "CURATION_DELETE", curationId, curation.getTitle()));
    }

    @Override
    public List<CurationResponseDto> getPublicCurations() {
        return curationRepository.findByIsDefaultTrueOrEndAtAfterOrderByDisplayOrderAsc(LocalDateTime.now())
                .stream().map(CurationResponseDto::from).toList();
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try { return LocalDate.parse(dateStr).atStartOfDay(); }
        catch (Exception e) { throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)"); }
    }

    private List<DailyTrendDto> buildDailyTrend(LocalDate start, LocalDate end,
                                                List<Object[]> userRows, List<Object[]> tripRows, List<Object[]> viewRows) {
        Map<LocalDate, Long> userMap = toDateCountMap(userRows);
        Map<LocalDate, Long> tripMap = toDateCountMap(tripRows);
        Map<LocalDate, Long> viewMap = toDateCountMap(viewRows);

        List<DailyTrendDto> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            result.add(new DailyTrendDto(d.toString(),
                    userMap.getOrDefault(d, 0L), tripMap.getOrDefault(d, 0L), viewMap.getOrDefault(d, 0L)));
        }
        return result;
    }

    private Map<LocalDate, Long> toDateCountMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        return map;
    }

    private LocalDate toLocalDate(Object dateObj) {
        if (dateObj instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (dateObj instanceof LocalDate localDate) return localDate;
        if (dateObj instanceof LocalDateTime localDateTime) return localDateTime.toLocalDate();
        return LocalDate.parse(dateObj.toString());
    }
}