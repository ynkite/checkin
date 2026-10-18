package idusw.sbb.checkin.domain.auth;

import idusw.sbb.checkin.domain.auth.filter.JwtAuthenticationFilter;
import idusw.sbb.checkin.domain.auth.service.JwtProvider;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 토큰 한 장으로 어디까지 들어가는가 */
class JwtFilterStatusTest {

    JwtProvider jwt = new JwtProvider("0123456789abcdef0123456789abcdef-test", 60_000, 600_000);
    UserRepository users = mock(UserRepository.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void refresh_토큰은_access_토큰_대신_쓸_수_없다() throws Exception {
        assertThat(call(jwt.createRefreshToken(7L))).isNull();
    }

    @Test
    void 관리자_권한을_거두면_예전_관리자_토큰도_일반_회원이_된다() throws Exception {
        user(7L, "USER", "ACTIVE");
        Authentication a = call(jwt.createAccessToken(7L, "ADMIN"));
        assertThat(a.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void 탈퇴한_계정의_토큰은_인증하지_않는다() throws Exception {
        user(7L, "USER", "DELETED");
        assertThat(call(jwt.createAccessToken(7L, "USER"))).isNull();
    }

    @Test
    void 정지_계정은_인증은_유지한다_글쓰기만_서비스에서_막는다() throws Exception {
        user(7L, "USER", "SUSPENDED");
        assertThat(call(jwt.createAccessToken(7L, "USER"))).isNotNull();
    }

    private void user(Long id, String role, String status) {
        User u = User.builder().username("u").role(role).status(status).build();
        ReflectionTestUtils.setField(u, "id", id);
        when(users.findById(id)).thenReturn(Optional.of(u));
    }

    private Authentication call(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
