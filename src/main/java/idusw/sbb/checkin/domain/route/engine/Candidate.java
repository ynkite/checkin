package idusw.sbb.checkin.domain.route.engine;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * 동선 후보 장소 하나 (관광지/식당/카페). 후보 선별(연결성 데이터·필터)의 결과물이며,
 * 이 자료구조 자체는 선별 로직을 모른다 — 밴드 분할·슬롯 배정·순서 최적화가 소비하는 입력일 뿐이다.
 */
public record Candidate(
        String id,
        String name,
        GeoPoint location,
        CandidateCategory category,
        int stayMinutes,
        LocalTime openTime,
        LocalTime closeTime,
        Set<DayOfWeek> closedDays
) {

    public Candidate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (stayMinutes <= 0) {
            throw new IllegalArgumentException("stayMinutes must be positive");
        }
        if ((openTime == null) != (closeTime == null)) {
            throw new IllegalArgumentException("openTime and closeTime must be both set or both null");
        }
        closedDays = (closedDays == null || closedDays.isEmpty())
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(closedDays);
    }

    /** 영업시간·휴무일 기준으로 이 시각에 들어갈 수 있는지. openTime/closeTime 이 없으면 상시 영업으로 본다. */
    public boolean isOpenAt(DayOfWeek dayOfWeek, LocalTime time) {
        if (closedDays.contains(dayOfWeek)) {
            return false;
        }
        if (openTime == null) {
            return true;
        }
        return !time.isBefore(openTime) && !time.isAfter(closeTime);
    }

    public double distanceKmTo(GeoPoint point) {
        return location.distanceKmTo(point);
    }
}
