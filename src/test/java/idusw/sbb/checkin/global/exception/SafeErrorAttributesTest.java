package idusw.sbb.checkin.global.exception;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeErrorAttributesTest {

    private final SafeErrorAttributes attributes = new SafeErrorAttributes();

    // 설정 파일에서 include-* 를 전부 켠 상황
    private static final ErrorAttributeOptions ALL_ON = ErrorAttributeOptions.defaults()
            .including(Include.STACK_TRACE, Include.EXCEPTION, Include.MESSAGE, Include.BINDING_ERRORS);

    private Map<String, Object> render(int status, Throwable ex, ErrorAttributeOptions options) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trips/999999/routes");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ex);
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "idusw.sbb.checkin 내부 메시지");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/trips/999999/routes");
        return attributes.getErrorAttributes(new ServletWebRequest(request), options);
    }

    @Test
    void 설정으로_켜도_스택트레이스와_예외이름이_나가지_않는다() {
        Map<String, Object> body = render(404, new IllegalStateException("idusw.sbb 내부"), ALL_ON);

        assertThat(body).doesNotContainKeys("trace", "exception", "errors");
        assertThat(body.toString()).doesNotContain("idusw", "springframework", "\tat ");
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("message")).isEqualTo("찾는 정보가 없습니다.");
    }

    @Test
    void 서버_오류는_다시_시도하라는_문구만_남긴다() {
        Map<String, Object> body = render(500, new RuntimeException("DB 접속 정보"), ALL_ON);

        assertThat(body).doesNotContainKeys("trace", "exception");
        assertThat(body.toString()).doesNotContain("DB 접속 정보");
        assertThat(body.get("message")).isEqualTo("서버에서 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요.");
    }

    @Test
    void 상태값이_응답에서_빠져도_문구는_실제_상태를_따른다() {
        Map<String, Object> body = render(404, new IllegalStateException("x"), ErrorAttributeOptions.of());

        assertThat(body.get("message")).isEqualTo("찾는 정보가 없습니다.");
    }
}
