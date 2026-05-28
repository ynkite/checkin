// 로그인 성공 시 클라이언트에 돌려주는 토큰 응답
package idusw.sbb.triplinker.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponseDto {
    private String accessToken;
    private String refreshToken;

    // 90일 경과 시 프론트에서 모달 띄우도록 알려주는 플래그
    private boolean pwChangeRecommended;
}