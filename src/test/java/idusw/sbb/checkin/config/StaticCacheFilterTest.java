package idusw.sbb.checkin.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배포 주소에서 화면 하나 열 때마다 580KB 를 다시 받고 있었다. no-store 때문이다.
 * 이 필터가 정적 파일에서만 그걸 걷어낸다 — 화면과 API 는 건드리면 안 된다.
 */
class StaticCacheFilterTest {

    private final StaticCacheFilter filter = new StaticCacheFilter();

    private MockHttpServletResponse through(String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        /* 시큐리티가 붙이는 기본 머리말을 흉내 낸다 */
        res.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        filter.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                rs.getOutputStream().write(new byte[]{1});   /* 본문을 쓴다 = 머리말 확정 */
                super.doFilter(rq, rs);
            }
        });
        return res;
    }

    @Test
    void 정적_파일은_사본을_남기게_둔다() throws Exception {
        for (String uri : new String[]{"/css/styles_map.css", "/js/live-voice.js",
                                       "/img/mass_haeundae.svg", "/fonts/a.woff2", "/uploads/posts/1.png"}) {
            String cc = through(uri).getHeader("Cache-Control");
            /* no-store 가 남아 있으면 브라우저가 사본을 못 남겨 매번 다시 받는다 */
            assertThat(cc).as(uri).isEqualTo("no-cache");
        }
    }

    @Test
    void 화면과_API_는_건드리지_않는다() throws Exception {
        for (String uri : new String[]{"/", "/api/live/now", "/api/trips", "/plan/view"}) {
            String cc = through(uri).getHeader("Cache-Control");
            assertThat(cc).as(uri).contains("no-store");
        }
    }

    @Test
    void 오래_잡아_두지는_않는다() throws Exception {
        /* max-age 를 걸면 버전 표가 안 바뀐 파일이 낡은 채로 남는다.
           실제로 app_main.js 를 고치고도 ?v= 는 그대로였다. */
        assertThat(through("/js/app_main.js").getHeader("Cache-Control")).doesNotContain("max-age");
    }
}
