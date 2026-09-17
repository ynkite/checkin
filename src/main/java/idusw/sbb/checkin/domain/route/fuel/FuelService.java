package idusw.sbb.checkin.domain.route.fuel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

// 싼 주유소 — 현재 위치 반경에서, 돌아가는 값이 아까울 만큼 싼 곳만 골라 준다.
// 좌표: 브라우저는 WGS84, 오피넷은 KATEC → 카카오 좌표변환(기존 키)으로 바꾼다.
@Service
@RequiredArgsConstructor
public class FuelService {

    private final RestTemplate restTemplate;   // AppConfig 타임아웃 적용된 것
    private final ObjectMapper objectMapper;

    @Value("${opinet.api.key}")           private String opinetKey;
    @Value("${opinet.api.base-url}")      private String opinetBase;
    @Value("${kakao.rest.api.key}")       private String kakaoKey;

    private static final int DEFAULT_TANK_L = 40;      // 주유량 기본
    private static final double DEFAULT_FUEL_EFF = 12; // 연비 기본(가정값) km/L
    private static final int SAVING_MARGIN = 2000;     // 이만큼은 더 아껴야 권한다

    // 한 후보의 이득 판정 (순수) — 절감액/추가비용/순이득/권장여부
    public record Eval(long savingWon, long extraCostWon, long netWon, boolean recommend) {}

    static Eval evaluate(double areaAvg, double price, double distanceM, double fuelEff, int tankL) {
        long saving = Math.round((areaAvg - price) * tankL);
        // 추가거리 근사 = 왕복(현재→주유소→복귀). 티맵 정밀 우회는 이후 보정.
        double extraKm = (distanceM / 1000.0) * 2.0;
        long extraCost = Math.round((extraKm / fuelEff) * price);
        long net = saving - extraCost;
        boolean rec = saving > extraCost + SAVING_MARGIN;
        return new Eval(saving, extraCost, net, rec);
    }

    // 추천 주유소 목록 — 권장 조건을 넘는 것만, 순이득 큰 순
    public FuelResult recommend(double lat, double lng, Double fuelEff, Integer tankL, int radiusM) {
        double eff = (fuelEff != null && fuelEff > 0) ? fuelEff : DEFAULT_FUEL_EFF;
        boolean effAssumed = (fuelEff == null || fuelEff <= 0);
        int tank = (tankL != null && tankL > 0) ? tankL : DEFAULT_TANK_L;

        double[] ktm = toKatec(lat, lng);
        if (ktm == null) return new FuelResult(List.of(), effAssumed, eff, "좌표 변환에 실패했습니다.");

        List<Raw> raw = stations(ktm[0], ktm[1], radiusM);
        if (raw.isEmpty()) return new FuelResult(List.of(), effAssumed, eff, "주변 주유소를 찾지 못했습니다.");

        double avg = raw.stream().mapToDouble(Raw::price).average().orElse(0);
        List<FuelStation> out = new ArrayList<>();
        for (Raw r : raw) {
            Eval e = evaluate(avg, r.price(), r.dist(), eff, tank);
            if (e.recommend()) {
                out.add(new FuelStation(r.name(), (long) r.price(), Math.round(r.dist()),
                        e.savingWon(), e.extraCostWon(), e.netWon()));
            }
        }
        out.sort((a, b) -> Long.compare(b.netWon(), a.netWon()));
        String note = effAssumed ? "연비 " + (int) eff + "km/L 는 가정값입니다." : null;
        return new FuelResult(out, effAssumed, eff, note);
    }

    private record Raw(String name, double price, double dist) {}

    // WGS84 → KATEC(KTM). 카카오 좌표변환 API.
    private double[] toKatec(double lat, double lng) {
        try {
            String url = "https://dapi.kakao.com/v2/local/geo/transcoord.json"
                    + "?x=" + lng + "&y=" + lat + "&input_coord=WGS84&output_coord=KTM";
            HttpHeaders h = new HttpHeaders();
            h.set("Authorization", "KakaoAK " + kakaoKey);
            String body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
            JsonNode doc = objectMapper.readTree(body).path("documents");
            if (doc.isArray() && doc.size() > 0) {
                return new double[]{ doc.get(0).path("x").asDouble(), doc.get(0).path("y").asDouble() };
            }
        } catch (Exception e) {
            System.err.println("[Fuel] 좌표 변환 실패: " + e.getMessage());
        }
        return null;
    }

    // 오피넷 반경 주유소 (휘발유 B027, 가격 낮은 순)
    private List<Raw> stations(double x, double y, int radiusM) {
        List<Raw> out = new ArrayList<>();
        try {
            String url = opinetBase + "/aroundAll.do?out=json&code=" + opinetKey
                    + "&x=" + x + "&y=" + y + "&radius=" + radiusM + "&sort=2&prodcd=B027";
            String body = restTemplate.getForObject(url, String.class);
            JsonNode oil = objectMapper.readTree(body).path("RESULT").path("OIL");
            if (oil.isArray()) {
                for (JsonNode o : oil) {
                    out.add(new Raw(o.path("OS_NM").asText(""),
                            o.path("PRICE").asDouble(0), o.path("DISTANCE").asDouble(0)));
                }
            }
        } catch (Exception e) {
            System.err.println("[Fuel] 오피넷 조회 실패: " + e.getMessage());
        }
        return out;
    }

    public record FuelStation(String name, long price, long distanceM,
                              long savingWon, long extraCostWon, long netWon) {}
    public record FuelResult(List<FuelStation> stations, boolean fuelEffAssumed,
                             double fuelEff, String note) {}

    // 자체 검증 — 순수 계산
    public static void main(String[] args) {
        // 평균 1850, 이 주유소 1810, 1km 거리, 연비 12, 40L
        Eval e = evaluate(1850, 1810, 1000, 12, 40);
        // 절감 = (1850-1810)*40 = 1600
        assert e.savingWon() == 1600 : "절감액: " + e.savingWon();
        // 추가거리 2km, 추가비용 = 2/12*1810 ≈ 302
        assert e.extraCostWon() == 302 : "추가비용: " + e.extraCostWon();
        assert !e.recommend() : "1600 < 302+2000 → 권장 안 함";
        // 훨씬 싼 곳: 평균 1900, 이 주유소 1800, 0.5km
        Eval e2 = evaluate(1900, 1800, 500, 12, 40);
        assert e2.savingWon() == 4000 : "절감: " + e2.savingWon();
        assert e2.recommend() : "4000 > 추가비용+2000 → 권장";
        System.out.println("OK 주유소 판정 정상 saving=" + e2.savingWon() + " net=" + e2.netWon());
    }
}
