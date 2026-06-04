package idusw.sbb.triplinker.global.exception;

public class SocialAccountExistException extends RuntimeException{
    private final String provider;

    public SocialAccountExistException(String provider) {
        super("이미 소셜 계정으로 가입된 이메일입니다.");
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}