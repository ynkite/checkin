// 로그인 성공 시 클라이언트에 돌려주는 토큰 응답
package idusw.sbb.triplinker.domain.auth.dto;

public record TokenResponseDto(
        String accessToken,
        String refreshToken,
        //90일 비번 변경 팝업용
        boolean isPasswordChangeRecommended
) {}