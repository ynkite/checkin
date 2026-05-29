// 로그인 요청 시 받는 데이터 (username + password)
package idusw.sbb.triplinker.domain.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequestDto {
    private String username;
    private String password;
}