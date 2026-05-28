package idusw.sbb.triplinker.domain.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserNicknameUpdateRequest {
    private String name; // 새로 변경할 닉네임(이름)
}