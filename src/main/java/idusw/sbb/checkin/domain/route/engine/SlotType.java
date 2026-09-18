package idusw.sbb.checkin.domain.route.engine;

/**
 * 하루 리듬 상의 슬롯 구분. 실제 시각은 배정하지 않는다 — 순서일 뿐이다.
 */
public enum SlotType {
    MORNING_ACTIVITY,
    LUNCH,
    AFTERNOON_ACTIVITY,
    DINNER,
    EVENING_ACTIVITY
}
