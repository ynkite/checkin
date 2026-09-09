package idusw.sbb.triplinker.global.util;

import java.security.SecureRandom;
import java.util.Base64;

// 공유·구독 링크용 추측 불가능 토큰 생성기.
// XOR 같은 역산 가능한 방식을 쓰지 않는다 — 토큰 하나가 유출돼도 다른 토큰을 유도할 수 없어야 한다.
public final class ShareTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    // 24바이트(192비트) → base64url 32자. 순차 추측·브루트포스 불가.
    private static final int BYTES = 24;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ShareTokenGenerator() {}

    // URL 경로에 그대로 넣을 수 있는 32자 랜덤 토큰
    public static String generate() {
        byte[] buf = new byte[BYTES];
        RANDOM.nextBytes(buf);
        return ENCODER.encodeToString(buf);
    }

    // 자체 검증
    public static void main(String[] args) {
        String a = generate();
        String b = generate();
        assert a.length() == 32 : "토큰 길이는 32자여야 한다: " + a.length();
        assert !a.equals(b) : "매 호출마다 달라야 한다";
        assert a.matches("[A-Za-z0-9_-]+") : "URL-safe 문자만: " + a;
        System.out.println("OK " + a + " " + b);
    }
}
