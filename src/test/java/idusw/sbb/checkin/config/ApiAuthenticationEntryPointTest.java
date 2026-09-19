package idusw.sbb.checkin.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAuthenticationEntryPointTest {

    private MockHttpServletRequest req(String uri, String accept) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        if (accept != null) r.addHeader("Accept", accept);
        return r;
    }

    @Test
    void fetch_로_부른_api_는_API_요청이다() {
        assertThat(ApiAuthenticationEntryPoint.isApiRequest(req("/api/admin/users", "*/*"))).isTrue();
        assertThat(ApiAuthenticationEntryPoint.isApiRequest(req("/api/admin/users", "application/json"))).isTrue();
        assertThat(ApiAuthenticationEntryPoint.isApiRequest(req("/api/admin/users", null))).isTrue();
    }

    @Test
    void 화면_주소와_주소창에서_연_api_는_로그인_화면_흐름을_따른다() {
        assertThat(ApiAuthenticationEntryPoint.isApiRequest(req("/admin", "*/*"))).isFalse();
        assertThat(ApiAuthenticationEntryPoint.isApiRequest(req("/apiary", "*/*"))).isFalse();
        assertThat(ApiAuthenticationEntryPoint.isApiRequest(
                req("/api/admin/users", "text/html,application/xhtml+xml,*/*;q=0.8"))).isFalse();
    }

    @Test
    void 응답은_401_JSON_이고_한글이_깨지지_않는다() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        new ApiAuthenticationEntryPoint().commence(req("/api/admin/users", "*/*"), res,
                new InsufficientAuthenticationException("no token"));

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(res.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(res.getContentAsString()).isEqualTo("{\"success\":false,\"message\":\"로그인이 필요합니다.\",\"data\":null}");
    }
}
