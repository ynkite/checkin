package idusw.sbb.checkin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 로그인 안 된 /api 요청에 로그인 화면 HTML 대신 401 JSON 을 준다.
 *
 * oauth2Login().loginPage("/") 가 만드는 기본 진입점은 요청을 「/」로 보낸다(302).
 * fetch 는 이것을 따라가 200 에 HTML 을 받고, JSON 으로 읽다가 터졌다.
 * 화면 요청(Accept 에 text/html)은 지금처럼 로그인 화면으로 보낸다.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** 본문 모양은 ApiResponse.error 와 같다 — 화면 JS 가 success·message 를 읽는다 */
    static final String BODY = "{\"success\":false,\"message\":\"로그인이 필요합니다.\",\"data\":null}";

    /** SecurityConfig 의 defaultAuthenticationEntryPointFor 에 넘긴다 */
    public static final RequestMatcher API_REQUEST = ApiAuthenticationEntryPoint::isApiRequest;

    static boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;
        if (!path.startsWith("/api/")) return false;
        /* 주소창에 /api/... 를 직접 친 경우처럼 HTML 을 원하는 요청은 화면 흐름을 따른다 */
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept == null || !accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(BODY);
    }
}
