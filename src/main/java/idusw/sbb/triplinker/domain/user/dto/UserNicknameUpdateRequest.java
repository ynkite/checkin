package idusw.sbb.triplinker.domain.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 회원 닉네임 변경 요청 시 클라이언트로부터 수정할 닉네임을 전달받는 Request DTO
// JSON 데이터의 'name' 필드를 자바 객체로 매핑

@Getter
@NoArgsConstructor
public class UserNicknameUpdateRequest {
    private String name; // 새로 변경할 닉네임(이름)
}