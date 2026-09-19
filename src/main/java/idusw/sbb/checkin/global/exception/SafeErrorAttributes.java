package idusw.sbb.checkin.global.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import jakarta.servlet.RequestDispatcher;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * 기본 오류 응답에서 스택트레이스·예외 클래스·내부 메시지를 뺀다.
 * server.error.include-* 설정은 properties 에 두면 배포 환경마다 달라지므로
 * 여기서 값과 상관없이 고정한다. 사용자에게는 상태별 안내 문구만 남긴다.
 */
@Component
public class SafeErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        ErrorAttributeOptions safe = options.excluding(
                ErrorAttributeOptions.Include.STACK_TRACE,
                ErrorAttributeOptions.Include.EXCEPTION,
                ErrorAttributeOptions.Include.MESSAGE,
                ErrorAttributeOptions.Include.BINDING_ERRORS);
        Map<String, Object> attrs = super.getErrorAttributes(webRequest, safe);
        attrs.remove("trace");
        attrs.remove("exception");
        attrs.remove("errors");

        // status 는 옵션에 따라 attrs 에 없을 수 있어 요청에서 직접 읽는다
        Object status = webRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
        attrs.put("message", messageFor(status instanceof Integer code ? code : 500));
        return attrs;
    }

    private String messageFor(int status) {
        HttpStatus s = HttpStatus.resolve(status);
        if (s == null) return "요청을 처리하지 못했습니다.";
        return switch (s) {
            case BAD_REQUEST -> "요청 내용을 다시 확인해 주세요.";
            case UNAUTHORIZED -> "로그인이 필요합니다.";
            case FORBIDDEN -> "이 기능을 쓸 권한이 없습니다.";
            case NOT_FOUND -> "찾는 정보가 없습니다.";
            case METHOD_NOT_ALLOWED -> "지원하지 않는 요청 방식입니다.";
            default -> s.is5xxServerError()
                    ? "서버에서 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요."
                    : "요청을 처리하지 못했습니다.";
        };
    }
}
