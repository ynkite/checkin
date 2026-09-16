package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelLeg;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelPlan;
import idusw.sbb.checkin.domain.route.tmap.dto.TravelPlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 출발지에서 시작해 들르는 순서대로 구간 이동시간을 낸다.
 *
 * 왜 필요한가 — 지금까지 화면의 「12분」은 직선거리로 만든 값이었다.
 * 실제로는 신호와 교통량이 있다. 출발지를 받아 첫 구간부터 재야
 * 「몇 시에 나가면 되는가」를 말할 수 있다.
 *
 * 한 구간이 실패하면 그 구간만 unknown 으로 두고 나머지는 계속 잰다.
 * 합계는 잰 구간만 더하고, 못 잰 구간이 있으면 note 로 알린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTimeService {

    private final TmapClient tmap;

    public TravelPlan plan(TravelPlanRequest req) {
        String mode = req.modeOr("CAR");
        List<TravelPlanRequest.Stop> stops = req.stops() == null ? List.of() : req.stops();

        if (stops.isEmpty()) {
            return new TravelPlan(tmap.ready(), mode, req.originName(),
                    req.originLat(), req.originLng(), List.of(), 0, 0, 0,
                    "들를 곳이 없습니다.");
        }

        /* 출발지 좌표. 글자만 왔으면 POI 검색으로 찾는다. */
        double[] origin = resolveOrigin(req);
        String originName = req.originName() == null || req.originName().isBlank()
                ? "출발지" : req.originName();

        if (!tmap.ready()) {
            List<TravelLeg> legs = new ArrayList<>();
            String from = originName;
            for (TravelPlanRequest.Stop s : stops) {
                legs.add(TravelLeg.unknown(from, s.name(), "이동시간 연동 전"));
                from = s.name();
            }
            return TravelPlan.notReady(mode, originName, legs,
                    "TMAP 키가 아직 없어 이동시간을 재지 못했습니다.");
        }

        if (origin == null) {
            List<TravelLeg> legs = new ArrayList<>();
            String from = originName;
            for (TravelPlanRequest.Stop s : stops) {
                legs.add(TravelLeg.unknown(from, s.name(), "출발지를 찾지 못했습니다"));
                from = s.name();
            }
            return new TravelPlan(true, mode, originName, null, null, legs,
                    null, null, null, "출발지를 찾지 못했습니다. 다른 이름으로 넣어 보세요.");
        }

        List<TravelLeg> legs = new ArrayList<>(stops.size());
        double fx = origin[1], fy = origin[0];          // x=경도, y=위도
        String fromName = originName;
        int sumMin = 0, sumM = 0, sumFare = 0, missed = 0;

        /* 첫 구간만 출발 시각을 쓴다. 뒤 구간은 앞 구간이 끝나는 시각이
           달라지므로 여기서 추정하지 않는다 — 추정을 쌓으면 오차가 곱해진다. */
        String departAt = req.departAt();

        for (TravelPlanRequest.Stop s : stops) {
            if (s.lat() == null || s.lng() == null) {
                legs.add(TravelLeg.unknown(fromName, s.name(), "좌표가 없습니다"));
                missed++;
                fromName = s.name();
                continue;
            }
            TravelLeg leg = leg(mode, fx, fy, s.lng(), s.lat(), fromName, s.name(), departAt);
            legs.add(leg);
            if (leg.minutes() != null) {
                sumMin += leg.minutes();
                if (leg.meters() != null) sumM += leg.meters();
                if (leg.fare() != null) sumFare += leg.fare();
            } else {
                missed++;
            }
            fx = s.lng();
            fy = s.lat();
            fromName = s.name();
            departAt = null;
        }

        String note = missed == 0
                ? (req.departAt() != null ? "첫 구간은 출발 시각의 교통량 기준입니다." : "지금 교통량 기준입니다.")
                : missed + "개 구간은 길을 찾지 못했습니다. 나머지만 더한 값입니다.";

        return new TravelPlan(true, mode, originName, origin[0], origin[1], legs,
                sumMin, sumM, sumFare, note);
    }

    /* ── 구간 하나 ─────────────────────────────────────────── */

    private TravelLeg leg(String mode, double sx, double sy, double ex, double ey,
                          String from, String to, String departAt) {
        try {
            if ("TRANSIT".equals(mode)) return transitLeg(sx, sy, ex, ey, from, to);
            if ("WALK".equals(mode))    return walkLeg(sx, sy, ex, ey, from, to);
            return carLeg(sx, sy, ex, ey, from, to, departAt);
        } catch (Exception e) {
            log.warn("[tmap] 구간 {} -> {} 실패: {}", from, to, e.getMessage());
            return TravelLeg.unknown(from, to, "길을 찾지 못했습니다");
        }
    }

    private TravelLeg carLeg(double sx, double sy, double ex, double ey,
                             String from, String to, String departAt) {
        JsonNode res = tmap.carRoute(sx, sy, ex, ey, from, to, departAt);
        JsonNode p = firstFeatureProps(res);
        if (p == null) return TravelLeg.unknown(from, to, "길을 찾지 못했습니다");

        int sec = p.path("totalTime").asInt(0);
        int m   = p.path("totalDistance").asInt(0);
        int fee = p.path("totalFare").asInt(0);       // 통행료
        if (sec <= 0) return TravelLeg.unknown(from, to, "길을 찾지 못했습니다");

        return new TravelLeg(from, to, "CAR", ceilMin(sec), m,
                fee > 0 ? fee : null, null,
                departAt != null ? "출발 시각 교통량" : "지금 교통량");
    }

    private TravelLeg walkLeg(double sx, double sy, double ex, double ey, String from, String to) {
        JsonNode p = firstFeatureProps(tmap.walkRoute(sx, sy, ex, ey, from, to));
        if (p == null) return TravelLeg.unknown(from, to, "걷는 길을 찾지 못했습니다");
        int sec = p.path("totalTime").asInt(0);
        int m   = p.path("totalDistance").asInt(0);
        if (sec <= 0) return TravelLeg.unknown(from, to, "걷는 길을 찾지 못했습니다");
        return new TravelLeg(from, to, "WALK", ceilMin(sec), m, 0, null, "걸어서");
    }

    private TravelLeg transitLeg(double sx, double sy, double ex, double ey, String from, String to) {
        JsonNode res = tmap.transitRoute(sx, sy, ex, ey);
        if (res == null) return TravelLeg.unknown(from, to, "대중교통 길을 찾지 못했습니다");

        /* 길이 없으면 metaData 없이 result.status 로 온다 —
           11 = 출발·도착이 너무 가깝다, 14 = 길 없음. 그때는 걷는 쪽을 권한다. */
        JsonNode itineraries = res.path("metaData").path("plan").path("itineraries");
        if (!itineraries.isArray() || itineraries.isEmpty()) {
            int status = res.path("result").path("status").asInt(-1);
            String why = switch (status) {
                case 11 -> "걸어갈 수 있는 거리입니다";
                case 14 -> "대중교통 길이 없습니다";
                default -> "대중교통 길을 찾지 못했습니다";
            };
            return TravelLeg.unknown(from, to, why);
        }

        JsonNode it = itineraries.get(0);
        int sec = it.path("totalTime").asInt(0);
        int m   = it.path("totalDistance").asInt(0);
        int fare = it.path("fare").path("regularFare").path("totalFare").asInt(0);
        int tr   = it.path("transferCount").asInt(0);
        if (sec <= 0) return TravelLeg.unknown(from, to, "대중교통 길을 찾지 못했습니다");

        String note = tr > 0 ? ("환승 " + tr + "번") : "환승 없음";
        return new TravelLeg(from, to, "TRANSIT", ceilMin(sec), m,
                fare > 0 ? fare : null, tr, note);
    }

    /* ── 출발지 ────────────────────────────────────────────── */

    /** @return {위도, 경도} 또는 null */
    private double[] resolveOrigin(TravelPlanRequest req) {
        if (req.originLat() != null && req.originLng() != null) {
            return new double[]{req.originLat(), req.originLng()};
        }
        JsonNode res = tmap.pois(req.originName(), 1);
        if (res == null) return null;
        JsonNode poi = res.path("searchPoiInfo").path("pois").path("poi");
        JsonNode one = poi.isArray() ? (poi.isEmpty() ? null : poi.get(0)) : poi;
        if (one == null || one.isMissingNode()) return null;

        /* frontLat/frontLon 은 건물 앞(차가 설 수 있는 자리),
           noorLat/noorLon 은 건물 중심이다. 차로 갈 때는 앞이 맞다. */
        double lat = one.path("frontLat").asDouble(one.path("noorLat").asDouble(0));
        double lng = one.path("frontLon").asDouble(one.path("noorLon").asDouble(0));
        if (lat == 0 || lng == 0) return null;
        return new double[]{lat, lng};
    }

    /** 초를 분으로. 59초를 0분이라고 하지 않는다. */
    private int ceilMin(int sec) {
        return Math.max(1, (sec + 59) / 60);
    }

    private JsonNode firstFeatureProps(JsonNode res) {
        if (res == null) return null;
        JsonNode features = res.path("features");
        if (!features.isArray() || features.isEmpty()) return null;
        JsonNode p = features.get(0).path("properties");
        return p.isMissingNode() ? null : p;
    }
}
