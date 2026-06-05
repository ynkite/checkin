package idusw.sbb.triplinker.global.exception;

import lombok.Getter;

@Getter
public class LoginFailException extends RuntimeException {

    private final boolean locked;
    private final int failCount;
    private final long remainSeconds;

    public LoginFailException(boolean locked, int failCount, long remainSeconds) {
        super(locked ? "로그인 5회 실패로 계정이 잠겼습니다." : "아이디 또는 비밀번호가 올바르지 않습니다.");
        this.locked = locked;
        this.failCount = failCount;
        this.remainSeconds = remainSeconds;
    }
}