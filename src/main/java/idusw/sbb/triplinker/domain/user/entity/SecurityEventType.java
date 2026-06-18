package idusw.sbb.triplinker.domain.user.entity;

public enum SecurityEventType {
    PW_CHANGE,          // 비밀번호 변경 완료
    PW_CHANGE_NOTIFIED,  // 90일 경과 변경 권장 모달 노출
    LOGIN_FAIL,
    ACCOUNT_SUSPENDED,    // 관리자에 의한 계정 정지
    ACCOUNT_UNSUSPENDED,  // 관리자에 의한 정지 해제
}