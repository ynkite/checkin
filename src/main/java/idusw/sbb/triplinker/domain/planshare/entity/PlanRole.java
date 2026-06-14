package idusw.sbb.triplinker.domain.planshare.entity;

public enum PlanRole {
    OWNER,   // 소유자 (생성자)
    EDITOR,  // 편집자 (이메일 초대받은 그룹 멤버)
    READER   // 열람자 (링크로 들어온 사람)
}