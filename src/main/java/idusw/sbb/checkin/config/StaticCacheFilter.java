package idusw.sbb.checkin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 정적 파일의 Cache-Control 을 정한다.
 *
 * <p>스프링 시큐리티는 모든 응답에 {@code no-cache, no-store, max-age=0, must-revalidate}
 * 를 붙인다. 로그인한 화면(HTML) 에는 맞는 기본값이지만 그림·스크립트까지 걸려서
 * 두 번째 방문에도 파일 120개를 하나씩 다시 물어봤다. 느린 4G 에서는 한 번에 562ms 다.
 *
 * <ul>
 *   <li>{@code ?v=} 가 붙은 파일 — 내용이 바뀌면 표도 바뀐다는 약속이다. 1년 immutable.
 *       <b>고쳤으면 index.html 의 표를 올려야 한다.</b> 안 올리면 낡은 것을 쓴다</li>
 *   <li>표가 없는 그림·글씨·업로드 — 하루</li>
 *   <li>표가 없는 css·js — no-cache. 304 로 확인만 한다</li>
 * </ul>
 *
 * <p>화면(HTML)과 API 는 건드리지 않는다.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)   /* 시큐리티가 머리말을 쓴 뒤에 덮어야 한다 */
public class StaticCacheFilter extends OncePerRequestFilter {

    /** 사람이 만든 것이 아니라 빌드가 내놓는 것들. 여기만 손댄다 */
    private static final String[] STATIC = {
            "/css/", "/js/", "/img/", "/font/", "/fonts/", "/uploads/"
    };

    private static boolean isStatic(String uri) {
        for (String p : STATIC) if (uri.startsWith(p)) return true;
        return uri.equals("/favicon.ico") || uri.equals("/manifest.json");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!isStatic(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        /* 응답이 나가기 직전에 바꿔야 한다. 여기서 미리 set 해 봐야
           시큐리티 필터가 나중에 덮어쓴다. 첫 바이트가 나가는 순간 손본다. */
        String uri = req.getRequestURI(), q = req.getQueryString();
        boolean versioned = q != null && (q.startsWith("v=") || q.contains("&v="));
        boolean asset = !(uri.startsWith("/css/") || uri.startsWith("/js/"));
        String policy = versioned ? "public, max-age=31536000, immutable"
                      : asset     ? "public, max-age=86400"
                      :             "no-cache";
        chain.doFilter(req, new HttpServletResponseWrapperOnCommit(res, policy));
    }

    /**
     * 머리말이 확정되는 순간(본문이 나가기 직전)에 Cache-Control 만 바꾼다.
     * 서블릿은 한 번 보낸 머리말을 되돌릴 수 없어서, 보내기 전에 끼어들어야 한다.
     */
    private static final class HttpServletResponseWrapperOnCommit
            extends jakarta.servlet.http.HttpServletResponseWrapper {

        private boolean fixed = false;
        private final String policy;

        HttpServletResponseWrapperOnCommit(HttpServletResponse res, String policy) {
            super(res);
            this.policy = policy;
        }

        private void fix() {
            if (fixed) return;
            fixed = true;
            setHeader("Cache-Control", policy);
            setHeader("Pragma", "");
            setHeader("Expires", "");
        }

        @Override
        public jakarta.servlet.ServletOutputStream getOutputStream() throws IOException {
            fix();
            return super.getOutputStream();
        }

        @Override
        public java.io.PrintWriter getWriter() throws IOException {
            fix();
            return super.getWriter();
        }

        @Override
        public void setStatus(int sc) {
            fix();                       /* 304 도 여기를 지난다 */
            super.setStatus(sc);
        }

        @Override
        public void flushBuffer() throws IOException {
            fix();
            super.flushBuffer();
        }
    }
}
