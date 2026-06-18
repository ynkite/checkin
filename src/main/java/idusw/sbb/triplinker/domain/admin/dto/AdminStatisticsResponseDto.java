package idusw.sbb.triplinker.domain.admin.dto;

import java.util.List;

public record AdminStatisticsResponseDto(
        long visitCount,
        long newUsers,
        long newTrips,
        List<DestinationStatDto> popularDestinations,
        List<DailyTrendDto> dailyTrend
) {}