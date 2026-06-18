package idusw.sbb.triplinker.domain.admin.dto;

public record DailyTrendDto(String date, long newUsers, long newTrips, long viewCount) {}