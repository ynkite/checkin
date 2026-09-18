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
        Integer dwellMinutes,
        LocalTime openTime,
        LocalTime closeTime,
        Set<DayOfWeek> closedDays
) {

    /** 관광공사 데이터에 체류시간이 없을 때 쓰는 카테고리 기본값 (결정 9-2). 과대추정 쪽으로 잡는다. */
    private static final int DEFAULT_FOOD_DWELL_MINUTES = 60;
    private static final int DEFAULT_TOUR_DWELL_MINUTES = 90;
    private static final int DEFAULT_CAFE_DWELL_MINUTES = 40;

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
        if (dwellMinutes != null && dwellMinutes <= 0) {
            throw new IllegalArgumentException("dwellMinutes must be positive");
        }
        if ((openTime == null) != (closeTime == null)) {
            throw new IllegalArgumentException("openTime and closeTime must be both set or both null");
        }
        closedDays = (closedDays == null || closedDays.isEmpty())
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(closedDays);
    }

    /**
     * 비어 있으면 카테고리 기본값을 읽는 시점에 계산한다 — 생성자에서 박아 넣지 않는다.
     * 그래야 나중에 관광공사·카카오가 실제 체류시간을 주기 시작했을 때 "기본값이 채워진 것"과
     * "진짜 값"을 필드(null 여부)로 구분할 수 있다.
     */
    @Override
    public Integer dwellMinutes() {
        return dwellMinutes != null ? dwellMinutes : defaultDwellMinutes(category);
    }

    private static int defaultDwellMinutes(CandidateCategory category) {
        return switch (category) {
            case FOOD -> DEFAULT_FOOD_DWELL_MINUTES;
            case TOUR -> DEFAULT_TOUR_DWELL_MINUTES;
            case CAFE -> DEFAULT_CAFE_DWELL_MINUTES;
        };
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
