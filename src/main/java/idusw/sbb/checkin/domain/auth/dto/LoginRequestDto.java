// 로그인 요청 시 받는 데이터 (username + password)
package idusw.sbb.checkin.domain.auth.dto;

public record LoginRequestDto (
        String username,
        String password
) {}