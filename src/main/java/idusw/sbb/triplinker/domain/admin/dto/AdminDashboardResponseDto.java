package idusw.sbb.triplinker.domain.admin.dto;

import java.util.List;

public record AdminDashboardResponseDto(
        long totalUsers,
        long totalTrips,
        long totalPosts,
        long pendingReports,
        long totalPostViews,
        StatusBreakdownDto userStatusBreakdown,
        ReportStatusBreakdownDto reportStatusBreakdown,
        List<PopularPostDto> popularPosts
) {}