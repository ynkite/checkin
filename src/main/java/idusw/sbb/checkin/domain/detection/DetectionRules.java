package idusw.sbb.checkin.domain.detection;

// 실시간 변수 감지 규칙 (순수 함수). 임계값은 바깥(설정)에서 주입 — 코드에 박지 않는다.
// 3종 신호: 혼잡(집중률) · 날씨(비) · 동선(이동시간 지연).
public final class DetectionRules {

    // 하나의 감지 신호
    public record Signal(String type, boolean triggered, String reason, double value) {}

    public static final String CROWD = "CROWD";
    public static final String WEATHER = "WEATHER";
    public static final String ROUTE = "ROUTE";

    private DetectionRules() {}

    // 혼잡 — 예측 집중률이 임계 이상이면 붐빔. rate<0 이면 데이터 없음(감지 불가).
    public static Signal crowd(double predictedRate, double threshold) {
        if (predictedRate < 0) return new Signal(CROWD, false, "집중률 확인 중", predictedRate);
        boolean hit = predictedRate >= threshold;
        return new Signal(CROWD, hit, hit ? "다음 관광지 집중률 높음" : "혼잡하지 않음", predictedRate);
    }

    // 날씨 — 비 예보 플래그가 있거나 강수확률이 임계 이상이면 비.
    public static Signal weather(boolean rainExpected, Integer rainProb, int probThreshold) {
        boolean byProb = rainProb != null && rainProb >= probThreshold;
        boolean hit = rainExpected || byProb;
        double value = rainProb != null ? rainProb : -1;
        return new Signal(WEATHER, hit, hit ? "비 예보 — 실내 대안 권장" : "비 예보 없음", value);
    }

    // 동선 — 실측/예측 이동시간 비율이 임계 이상이면 지연. ratio null 이면 데이터 없음.
    public static Signal route(Double delayRatio, double threshold) {
        if (delayRatio == null) return new Signal(ROUTE, false, "이동시간 확인 중", -1);
        boolean hit = delayRatio >= threshold;
        return new Signal(ROUTE, hit, hit ? "이동시간 지연" : "이동 정상", delayRatio);
    }

    public static void main(String[] args) {
        // 혼잡
        assert crowd(85, 70).triggered() : "85>=70 붐빔";
        assert !crowd(50, 70).triggered() : "50<70 정상";
        assert !crowd(-1, 70).triggered() : "데이터 없으면 감지 안 함";
        // 날씨
        assert weather(true, null, 60).triggered() : "rainExpected 면 비";
        assert weather(false, 70, 60).triggered() : "70>=60 비";
        assert !weather(false, 30, 60).triggered() : "30<60 아님";
        assert !weather(false, null, 60).triggered() : "데이터 없으면 아님";
        // 동선
        assert route(1.4, 1.3).triggered() : "1.4>=1.3 지연";
        assert !route(1.1, 1.3).triggered() : "1.1<1.3 정상";
        assert !route(null, 1.3).triggered() : "데이터 없으면 감지 안 함";
        System.out.println("OK 감지 규칙 정상");
    }
}
