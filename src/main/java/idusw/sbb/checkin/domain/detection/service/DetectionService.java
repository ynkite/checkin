package idusw.sbb.checkin.domain.detection.service;

import idusw.sbb.checkin.domain.detection.DetectionRules;
import idusw.sbb.checkin.domain.detection.dto.DetectionContext;
import idusw.sbb.checkin.domain.detection.dto.DetectionResult;
import idusw.sbb.checkin.domain.detection.privacy.GeoMasker;
import idusw.sbb.checkin.domain.tour.dto.ConcentrationRate;
import idusw.sbb.checkin.domain.tour.service.ConcentrationService;
import idusw.sbb.checkin.domain.weather.dto.DayWeather;
import idusw.sbb.checkin.domain.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 실시간 변수 감지 — 혼잡·날씨·동선 신호를 모아 재계획 필요 여부를 낸다.
// 관광공사/날씨 API 는 실시간 호출(캐싱 금지). 하나가 죽어도 폴백으로 화면은 계속 뜬다.
@Service
@RequiredArgsConstructor
public class DetectionService {

    private final ConcentrationService concentrationService; // 이수환 — 집중률 예측 (수정 안 함, 주입만)
    private final WeatherService weatherService;             // 날씨 (주입만)

    // 임계값은 설정에서 (코드에 박지 않는다)
    @Value("${detection.crowd.rate-threshold:70}")
    private double crowdThreshold;
    @Value("${detection.weather.rain-prob-threshold:60}")
    private int rainProbThreshold;
    @Value("${detection.route.delay-threshold:1.3}")
    private double routeDelayThreshold;

    // 위치정보 가명처리 격자 자릿수 (2 ≈ 1.1km). 정밀 좌표는 서버에 남기지 않는다.
    @Value("${privacy.geo.grid-decimals:2}")
    private int geoGridDecimals;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public DetectionResult detect(DetectionContext ctx) {
        // 정밀 좌표가 들어오면 입구에서 즉시 격자화. 이후 정밀값은 절대 쓰거나 남기지 않는다.
        Double maskedLat = null, maskedLon = null;
        if (ctx.lat() != null && ctx.lon() != null) {
            double[] grid = GeoMasker.toGrid(ctx.lat(), ctx.lon(), geoGridDecimals);
            maskedLat = grid[0];
            maskedLon = grid[1];
        }

        List<DetectionRules.Signal> signals = new ArrayList<>();
        signals.add(detectCrowd(ctx));
        signals.add(detectWeather(ctx));
        signals.add(DetectionRules.route(ctx.routeDelayRatio(), routeDelayThreshold));
        return DetectionResult.of(signals, maskedLat, maskedLon);
    }

    // 혼잡 — 집중률 예측에서 다음 관광지·날짜의 예측값을 찾아 판정
    private DetectionRules.Signal detectCrowd(DetectionContext ctx) {
        double rate = -1; // 못 찾으면 감지 불가
        try {
            String targetYmd = ctx.travelDate() != null ? ctx.travelDate().format(YMD) : null;
            List<ConcentrationRate> rates =
                    concentrationService.predict(ctx.areaCd(), ctx.signguCd(), 100, 1);
            for (ConcentrationRate r : rates) {
                boolean placeMatch = ctx.nextPlaceName() != null && r.placeName() != null
                        && r.placeName().contains(ctx.nextPlaceName());
                boolean dateMatch = targetYmd == null || targetYmd.equals(r.date());
                if (placeMatch && dateMatch) {
                    rate = Math.max(rate, r.rate());
                }
            }
        } catch (Exception e) {
            // 폴백: 집중률 API 실패해도 감지 결과는 나가야 한다 (rate=-1 → "확인 중")
            System.err.println("[Detection] 집중률 조회 실패: " + e.getMessage());
        }
        return DetectionRules.crowd(rate, crowdThreshold);
    }

    // 날씨 — 방문 날짜의 비 예보로 판정
    private DetectionRules.Signal detectWeather(DetectionContext ctx) {
        try {
            DayWeather dw = weatherService.getDayWeather(ctx.region(), ctx.travelDate());
            if (dw != null) {
                return DetectionRules.weather(dw.isRainExpected(), dw.getRainProb(), rainProbThreshold);
            }
        } catch (Exception e) {
            System.err.println("[Detection] 날씨 조회 실패: " + e.getMessage());
        }
        return DetectionRules.weather(false, null, rainProbThreshold);
    }
}
