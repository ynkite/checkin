package idusw.sbb.checkin.domain.auth;

import idusw.sbb.checkin.domain.auth.repository.OAuthAccountRepository;
import idusw.sbb.checkin.domain.auth.repository.PasswordResetTokenRepository;
import idusw.sbb.checkin.domain.auth.repository.RefreshTokenRepository;
import idusw.sbb.checkin.domain.auth.service.AuthServiceImpl;
import idusw.sbb.checkin.domain.auth.service.JwtProvider;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import idusw.sbb.checkin.domain.user.repository.UserSecurityHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 휴대폰에서 로그아웃해도 PC 는 로그인 상태로 남아야 한다 */
class AuthLogoutTest {

    RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    AuthServiceImpl auth = new AuthServiceImpl(mock(UserRepository.class), mock(UserSecurityHistoryRepository.class),
            tokens, mock(PasswordResetTokenRepository.class), mock(BCryptPasswordEncoder.class),
            mock(JwtProvider.class), mock(OAuthAccountRepository.class));

    @Test
    void 로그아웃은_이_기기_토큰만_지운다() {
        auth.logout(7L, "phone-token");

        verify(tokens).deleteByUserIdAndTokenHash(eq(7L), argThat(h -> h.length() == 64 && !h.equals("phone-token")));
        verify(tokens, never()).deleteByUserId(anyLong());
    }

    @Test
    void 토큰을_안_보내면_아무것도_지우지_않는다() {
        auth.logout(7L, null);
        auth.logout(7L, " ");

        verifyNoInteractions(tokens);
    }
}
