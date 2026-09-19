package idusw.sbb.checkin.global.exception;

import idusw.sbb.checkin.global.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SocialAccountExistException.class)
    public ResponseEntity<Map<String, String>> handleSocialAccountExistException(SocialAccountExistException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("code", "SOCIAL_ACCOUNT_EXIST");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("provider", ex.getProvider());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(LoginFailException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleLoginFail(LoginFailException ex) {
        Map<String, Object> data = new HashMap<>();
        data.put("locked", ex.isLocked());
        data.put("failCount", ex.getFailCount());
        if (ex.isLocked()) {
            data.put("remainSeconds", ex.getRemainSeconds());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), data));
    }

    // 사유 누락, 존재하지 않는 ID 조회, 잘못된 상태 변경 시도 등
    // 프로젝트 전반에서 공통으로 던지는 일반 검증 예외를 한 곳에서 처리
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(RuntimeException ex) {
        // 숫자 변환 실패는 「For input string: "abc"」 같은 내부 문구라 그대로 내보내지 않는다
        String message = ex instanceof NumberFormatException ? "숫자로 읽을 수 없는 값이 들어왔습니다." : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    // 주소의 값 형식이 틀린 경우 (/api/trips/abc). 원래 메시지에 자바 타입 이름이 들어 있다
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("요청 값 '" + ex.getName() + "'의 형식이 올바르지 않습니다."));
    }
}