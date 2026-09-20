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
 * 그림·글씨·스타일을 한 번 받은 뒤에는 다시 안 받게 한다.
 *
 * <p><b>무슨 일이 있었나</b> — 배포 주소에서 재 보니 화면을 열 때마다 <b>580KB</b> 를
 * 통째로 다시 받고 있었다. 두 번째 방문도 똑같았다. 파일 71개가 전부 새로 내려왔다.
 *
 * <pre>
 *   Cache-Control: no-cache, no-store, max-age=0, must-revalidate
 * </pre>
 *
 * <p>스프링 시큐리티가 모든 응답에 기본으로 붙이는 머리말이다. 로그인한 사람의
 * 화면이 브라우저에 남으면 안 되니 맞는 기본값이다. 그런데 <b>그림과 스크립트까지</b>
 * 같이 걸린다. {@code no-store} 는 「아예 갖고 있지 마라」라서, 브라우저가 사본을
 * 못 남기고 매번 처음부터 받는다.
 *
 * <p>여행 중에 길에서 휴대폰으로 여는 화면이다. 화면 하나 옮길 때마다 580KB 면
 * 느린 것을 넘어 데이터를 쓴다.
 *
 * <p><b>왜 오래 캐시하지 않았나</b> — 파일 이름에 {@code ?v=20260620a} 같은 표가
 * 붙어 있어서 그걸 믿고 일주일쯤 잡아 둘 수도 있다. 그런데 <b>그 표가 실제로 안 바뀐다.</b>
 * 오늘 {@code app_main.js} 를 고쳤는데 표는 그대로였다. 표를 믿고 오래 잡아 두면
 * 고친 것을 못 받고 낡은 화면을 쓰게 된다. 고쳐 놓고 안 고쳐진 것보다 나쁜 건 없다.
 *
 * <p>그래서 {@code no-cache} 로 둔다. 「갖고 있되 쓰기 전에 물어봐라」다.
 * 안 바뀌었으면 서버가 304 만 돌려주고 본문은 안 보낸다. <b>낡을 위험은 0 이고
 * 오가는 양은 거의 0 이 된다.</b>
 *
 * <p>화면(HTML)과 API 는 건드리지 않는다. 거기는 원래 기본값이 맞다.
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
        chain.doFilter(req, new HttpServletResponseWrapperOnCommit(res));
    }

    /**
     * 머리말이 확정되는 순간(본문이 나가기 직전)에 Cache-Control 만 바꾼다.
     * 서블릿은 한 번 보낸 머리말을 되돌릴 수 없어서, 보내기 전에 끼어들어야 한다.
     */
    private static final class HttpServletResponseWrapperOnCommit
            extends jakarta.servlet.http.HttpServletResponseWrapper {

        private boolean fixed = false;

        HttpServletResponseWrapperOnCommit(HttpServletResponse res) { super(res); }

        private void fix() {
            if (fixed) return;
            fixed = true;
            /* no-store 를 걷어내는 것이 핵심이다. no-cache 는 「갖고 있되
               쓰기 전에 물어봐라」라서 304 를 받을 수 있다. */
            setHeader("Cache-Control", "no-cache");
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
