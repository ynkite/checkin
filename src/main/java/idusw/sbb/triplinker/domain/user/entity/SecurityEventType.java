package idusw.sbb.triplinker.domain.user.entity;

public enum SecurityEventType {
    PW_CHANGE,          // 비밀번호 변경 완료
    PW_CHANGE_NOTIFIED  // 90일 경과 변경 권장 모달 노출
}