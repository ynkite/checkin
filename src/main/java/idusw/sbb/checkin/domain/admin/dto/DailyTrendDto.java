package idusw.sbb.checkin.domain.admin.dto;

public record DailyTrendDto(String date, long newUsers, long newTrips, long viewCount) {}