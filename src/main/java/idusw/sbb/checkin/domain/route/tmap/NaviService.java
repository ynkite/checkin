package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.route.tmap.dto.NaviRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// 자차 경로 원응답(GeoJSON)에서 주행 네비용 데이터를 뽑는다.
// TmapClient.carRoute() 는 이수환이 만든 것 — 수정하지 않고 호출만 한다.
@Service
@RequiredArgsConstructor
public class NaviService {

    private final TmapClient tmapClient;

    // 현재위치 → 다음 목적지 자차 경로를 네비 형태로
    public NaviRoute driveRoute(double sx, double sy, double ex, double ey,
                                String startName, String endName) {
        if (!tmapClient.ready()) {
            return NaviRoute.notReady("티맵 연동 준비 중입니다.");
        }
        JsonNode res = tmapClient.carRoute(sx, sy, ex, ey, startName, endName, null);
        NaviRoute parsed = parse(res);
        return parsed != null ? parsed : NaviRoute.notReady("경로를 불러오지 못했습니다.");
    }

    // 주변 검색 — 현재 위치 반경에서 키워드로 (「근처 주유소」 등)
    public List<java.util.Map<String, Object>> nearby(double lat, double lon, String keyword, int radiusKm, int count) {
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        if (!tmapClient.ready()) return out;
        JsonNode res = tmapClient.poisAround(lat, lon, keyword, radiusKm, count);
        if (res == null) return out;
        JsonNode pois = res.path("searchPoiInfo").path("pois").path("poi");
        if (!pois.isArray()) return out;
        for (JsonNode p : pois) {
            String plat = p.path("frontLat").asText(p.path("noorLat").asText(""));
            String plon = p.path("frontLon").asText(p.path("noorLon").asText(""));
            if (plat.isBlank() || plon.isBlank()) continue;
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name", p.path("name").asText(""));
            m.put("lat", Double.parseDouble(plat));
            m.put("lng", Double.parseDouble(plon));
            m.put("distanceKm", p.path("radius").asDouble(0)); // 반경(거리) km
            out.add(m);
        }
        return out;
    }

    // GeoJSON FeatureCollection → NaviRoute.
    // LineString feature = 경로선, Point feature(description 있는 것) = 턴 안내점.
    static NaviRoute parse(JsonNode res) {
        if (res == null) return null;
        JsonNode features = res.path("features");
        if (!features.isArray() || features.isEmpty()) return null;

        List<double[]> line = new ArrayList<>();
        List<NaviRoute.Guide> guides = new ArrayList<>();
        int totalMeters = 0, totalSeconds = 0;

        for (JsonNode f : features) {
            JsonNode geom = f.path("geometry");
            JsonNode props = f.path("properties");
            String type = geom.path("type").asText("");

            // 전체 거리·시간은 첫 Point(출발점)의 properties 에 담겨 온다
            if (props.has("totalDistance") && totalMeters == 0) {
                totalMeters = props.path("totalDistance").asInt(0);
            }
            if (props.has("totalTime") && totalSeconds == 0) {
                totalSeconds = props.path("totalTime").asInt(0);
            }

            if ("LineString".equals(type)) {
                for (JsonNode c : geom.path("coordinates")) {
                    // 티맵 좌표는 [경도(x), 위도(y)] 순 → [lat,lng] 로 뒤집는다
                    line.add(new double[]{ c.get(1).asDouble(), c.get(0).asDouble() });
                }
            } else if ("Point".equals(type)) {
                String desc = props.path("description").asText("");
                if (!desc.isBlank()) {
                    JsonNode c = geom.path("coordinates");
                    guides.add(new NaviRoute.Guide(
                            c.get(1).asDouble(), c.get(0).asDouble(),
                            desc,
                            props.path("turnType").asInt(0),
                            props.path("pointType").asText("")));
                }
            }
        }
        if (line.isEmpty()) return null;
        return new NaviRoute(true, line, guides, totalMeters, totalSeconds, "지금 교통량 기준입니다.");
    }

    // 자체 검증 — 파싱이 깨지면 실패
    public static void main(String[] args) throws Exception {
        String sample = "{\"features\":["
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.0413,35.1151]},"
                + "\"properties\":{\"totalDistance\":14043,\"totalTime\":2760,\"pointType\":\"S\","
                + "\"turnType\":200,\"description\":\"출발\"}},"
                + "{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[129.0413,35.1151],[129.05,35.12]]},"
                + "\"properties\":{}},"
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.1604,35.1587]},"
                + "\"properties\":{\"turnType\":13,\"pointType\":\"E\",\"description\":\"해운대로 방면으로 우회전\"}}"
                + "]}";
        JsonNode node = new ObjectMapper().readTree(sample);
        NaviRoute r = parse(node);
        assert r != null && r.ready() : "파싱 성공해야";
        assert r.line().size() == 2 : "경로선 2점: " + r.line().size();
        assert r.line().get(0)[0] == 35.1151 && r.line().get(0)[1] == 129.0413 : "lat/lng 순서 뒤집기";
        assert r.guides().size() == 2 : "턴안내 2개(description 있는 것만): " + r.guides().size();
        assert r.totalMeters() == 14043 : "전체거리";
        assert r.guides().get(1).description().contains("우회전") : "턴 문구";
        System.out.println("OK 네비 파싱 정상 line=" + r.line().size() + " guides=" + r.guides().size());
    }
}
