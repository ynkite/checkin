package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.route.tmap.dto.NaviRoute;
import idusw.sbb.checkin.domain.route.tmap.dto.OptimizedRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // 경유지 최적화 — 출발→(여러 목적지)→도착 을 가장 짧게 도는 순서 + 경로선.
    // waypoints: 각 {id, name, lat, lng}. 최대 10곳(티맵 제한).
    public OptimizedRoute optimize(double sx, double sy, double ex, double ey,
                                   String startName, String endName,
                                   List<Map<String, Object>> waypoints) {
        if (!tmapClient.ready()) return OptimizedRoute.notReady("티맵 연동 준비 중입니다.");
        if (waypoints == null || waypoints.isEmpty())
            return OptimizedRoute.notReady("경유지가 없습니다.");
        if (waypoints.size() > 10)
            return OptimizedRoute.notReady("경유지는 최대 10곳입니다.");

        List<Map<String, Object>> via = new ArrayList<>();
        for (Map<String, Object> w : waypoints) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("viaPointId", String.valueOf(w.get("id")));
            v.put("viaPointName", w.get("name") == null ? "경유지" : w.get("name").toString());
            v.put("viaX", w.get("lng"));   // 경도
            v.put("viaY", w.get("lat"));   // 위도
            via.add(v);
        }
        JsonNode res = tmapClient.routeOptimization(sx, sy, ex, ey, startName, endName, via);
        OptimizedRoute parsed = parseOptimize(res);
        return parsed != null ? parsed : OptimizedRoute.notReady("최적 경로를 불러오지 못했습니다.");
    }

    // routeOptimization10 응답(GeoJSON) → 방문 순서(viaPointId) + 경로선.
    static OptimizedRoute parseOptimize(JsonNode res) {
        if (res == null) return null;
        JsonNode features = res.path("features");
        if (!features.isArray() || features.isEmpty()) return null;

        List<String> order = new ArrayList<>();
        List<double[]> line = new ArrayList<>();
        // 전체 거리·시간은 최상위 properties 에 문자열로 온다
        JsonNode top = res.path("properties");
        int totalMeters = top.path("totalDistance").asInt(0);
        int totalSeconds = top.path("totalTime").asInt(0);

        for (JsonNode f : features) {
            JsonNode geom = f.path("geometry");
            JsonNode props = f.path("properties");
            String type = geom.path("type").asText("");

            if ("LineString".equals(type)) {
                for (JsonNode c : geom.path("coordinates"))
                    line.add(new double[]{ c.get(1).asDouble(), c.get(0).asDouble() });
            } else if ("Point".equals(type)) {
                /* 경유지 Point 는 viaPointId 를 달고 방문 순서대로 온다. 한 경유지가 도착·출발
                   두 점으로 나뉘어 와서 id 가 두 번씩 찍힌다(실호출로 확인 — v0,v0,v2,v2,v1,v1).
                   그대로 두면 「경유지 3곳인데 순서가 6개」가 되어 호출부가 응답을 버린다 */
                JsonNode vid = props.path("viaPointId");
                String id = vid.isMissingNode() ? "" : vid.asText("");
                if (!id.isBlank() && !order.contains(id)) order.add(id);
            }
        }
        if (line.isEmpty()) return null;
        return new OptimizedRoute(true, order, line, totalMeters, totalSeconds, "지금 교통량 기준입니다.");
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

        // 경유지 최적화 파싱 — viaPointId 를 방문 순서대로 뽑는다
        // 실제 응답처럼 전체 거리·시간은 최상위 properties, viaPointId 는 Point 에.
        String opt = "{\"properties\":{\"totalDistance\":\"21000\",\"totalTime\":\"3600\"},\"features\":["
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.04,35.11]},"
                + "\"properties\":{\"viaPointId\":\"\",\"pointType\":\"S\"}},"
                + "{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[129.04,35.11],[129.10,35.15]]},\"properties\":{\"viaPointId\":\"B\"}},"
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.10,35.15]},\"properties\":{\"viaPointId\":\"B\",\"pointType\":\"B1\"}},"
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.16,35.16]},\"properties\":{\"viaPointId\":\"A\",\"pointType\":\"B2\"}},"
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.20,35.20]},\"properties\":{\"viaPointId\":\"\",\"pointType\":\"E\"}}"
                + "]}";
        OptimizedRoute o = parseOptimize(new ObjectMapper().readTree(opt));
        assert o != null && o.ready() : "최적화 파싱 성공해야";
        assert o.order().equals(List.of("B", "A")) : "방문 순서: " + o.order();
        assert o.totalMeters() == 21000 : "전체거리: " + o.totalMeters();
        assert o.line().get(0)[0] == 35.11 : "lat/lng 뒤집기";
        System.out.println("OK 최적화 파싱 정상 order=" + o.order());
    }
}
