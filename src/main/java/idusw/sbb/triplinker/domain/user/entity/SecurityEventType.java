package idusw.sbb.triplinker.domain.user.entity;

public enum SecurityEventType {
    PW_CHANGE,          // 비밀번호 변경 완료
    PW_CHANGE_NOTIFIED,  // 90일 경과 변경 권장 모달 노출
    LOGIN_FAIL,  // 💡 이 코드가 누적/선언되어 있는지 꼭 확인하세요!
}