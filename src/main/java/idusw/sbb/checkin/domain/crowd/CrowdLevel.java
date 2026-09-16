package idusw.sbb.checkin.domain.crowd;

/**
 * 혼잡도 다섯 단계.
 *
 * 관광공사 집중률(cnctrRate)은 0~100 이다. 경계는 실제 분포에서 잡았다 —
 * 2026-09-16 에 다섯 시군구(부산 해운대 · 서울 강남 · 제주시 · 강원 강릉 등)
 * 30일분 1,770건을 받아 5분위로 나눈 값이다.
 *
 *   p80 86.2  p60 71.3  p30 52.2  p10 34.7   (중앙값 65.7 · 평균 64.2)
 *
 * 경계를 85 / 70 / 52 / 35 로 둔다. 70 은 백엔드 감지 임계
 * (detection.crowd.rate-threshold) 와 같은 자리다 — 화면의 「혼잡」과
 * 재계획을 권하는 자리가 어긋나면 설명할 수 없다.
 *
 * 화면(static/js/crowd.js)도 같은 경계를 쓴다. 한쪽만 바꾸면 안 된다.
 */
public enum CrowdLevel {

    VERY_HIGH("vhigh", "매우 혼잡", 85),
    HIGH     ("high",  "혼잡",     70),
    NORMAL   ("mid",   "정상",     52),
    LOW      ("low",   "한적",     35),
    VERY_LOW ("vlow",  "매우 한적", 0);

    private final String key;
    private final String label;
    private final int min;

    CrowdLevel(String key, String label, int min) {
        this.key = key;
        this.label = label;
        this.min = min;
    }

    public String key()   { return key; }
    public String label() { return label; }
    public int    min()   { return min; }

    /** 집중률 -> 단계. 음수는 「모름」이므로 null 을 돌려준다. */
    public static CrowdLevel of(double rate) {
        if (rate < 0) return null;
        for (CrowdLevel l : values()) {
            if (rate >= l.min) return l;
        }
        return VERY_LOW;
    }

    public static void main(String[] args) {
        assert of(92) == VERY_HIGH : "92 매우 혼잡";
        assert of(85) == VERY_HIGH : "경계는 위쪽에 붙는다";
        assert of(84.9) == HIGH : "84.9 혼잡";
        assert of(70) == HIGH;
        assert of(69.9) == NORMAL;
        assert of(52) == NORMAL;
        assert of(51.9) == LOW;
        assert of(35) == LOW;
        assert of(34.9) == VERY_LOW;
        assert of(0) == VERY_LOW;
        assert of(-1) == null : "모르면 단계가 없다";
        System.out.println("OK 혼잡도 5단계");
    }
}
