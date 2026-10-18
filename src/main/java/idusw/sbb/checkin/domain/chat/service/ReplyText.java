package idusw.sbb.checkin.domain.chat.service;

import java.util.regex.Pattern;

/** 모델 답 뒷손질. 화면용은 태그를 남기고, 음성용은 태그를 뗀다. */
public final class ReplyText {

    /* 맨 앞 「안녕하세요! 체크인입니다.」 류 인사 */
    private static final Pattern GREETING = Pattern.compile("^\\s*안녕하세요[!.,~ ]*(체크인[^.!\\n]{0,12}입니다[!.]*\\s*)?");
    /* 그림 문자. ℃ 같은 글자 꼴 기호(U+2100~214F)는 남긴다 */
    private static final Pattern EMOJI = Pattern.compile("[\\p{So}&&[^\\u2100-\\u214F]]|[\\uFE0F\\u200D]");
    private static final Pattern TAG = Pattern.compile("\\[(UPDATE|EXTRA):[^\\]]*\\]");

    private ReplyText() {}

    public static String tidy(String s) {
        if (s == null) return "";
        return EMOJI.matcher(GREETING.matcher(s).replaceFirst("")).replaceAll("").trim();
    }

    public static String untag(String s) {
        return s == null ? "" : TAG.matcher(s).replaceAll("").trim();
    }
}
