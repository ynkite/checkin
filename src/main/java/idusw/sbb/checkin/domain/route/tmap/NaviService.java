package idusw.sbb.checkin.domain.route.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.route.tmap.dto.NaviRoute;
import idusw.sbb.checkin.domain.route.tmap.dto.OptimizedRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// 자차 경로 원응답(GeoJSON)에서 주행 네비용 데이터를 뽑는다.
// TmapClient.carRoute() 는 이수환이 만든 것 — 시그니처는 그대로 두고 호출만 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class NaviService {

    private final TmapClient tmapClient;
    private final SafetySeed safetySeed;

    /* 티맵 시설물 코드 — 실제 응답으로 확인한 것만 쓴다.
       확인 방법: 시설물 구간 바로 앞 안내점이 그 시설 이름을 달고 온다.
         "남산3호터널에서 터널 후 …"      -> 뒤 구간 facilityType 2
         "이태원 지하차도에서 지하차도 후 …" -> 뒤 구간 facilityType 4
         "강남터미널 고가차도에서 고가도로 후 …" -> 뒤 구간 facilityType 3
         "인천대교 고속도로" 19km 구간      -> facilityType 1
       5 도 자주 나오는데 무엇인지 확인하지 못했다. 그래서 넣지 않는다. */
    private static final int FACILITY_BRIDGE = 1;
    private static final int FACILITY_TUNNEL = 2;
    private static final int FACILITY_OVERPASS = 3;
    private static final int FACILITY_UNDERPASS = 4;

    /* 제한속도를 도로 한 곳 물어보는 데 한 번씩 HTTP 가 나간다. 경로 하나에 구간이
       스무 개 넘게 나오니 병렬로 부르고 총 호출 수에 뚜껑을 씌운다. */
    private static final int SPEED_LOOKUP_BUDGET = 50;
    private static final int SPEED_MIN_SECTION_METERS = 30;

    private final ExecutorService speedPool = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "navi-speed");
        t.setDaemon(true);
        return t;
    });

    /** 도로 링크 한 줄 — 제한속도와 그 속도가 붙은 도로 이름. */
    private record Road(int speed, int lanes, String name) {}

    /* 같은 링크를 다시 묻지 않는다. 이탈 재탐색 때 앞 구간이 통째로 똑같이 온다 */
    private final Map<String, Road> roadCache = new ConcurrentHashMap<>();

    // 현재위치 → 다음 목적지 자차 경로를 네비 형태로
    public NaviRoute driveRoute(double sx, double sy, double ex, double ey,
                                String startName, String endName) {
        if (!tmapClient.ready()) {
            return NaviRoute.notReady("티맵 연동 준비 중입니다.");
        }
        JsonNode res = tmapClient.carRoute(sx, sy, ex, ey, startName, endName, null);
        NaviRoute parsed = parse(res);
        if (parsed == null) return NaviRoute.notReady("경로를 불러오지 못했습니다.");
        return enrich(parsed);
    }

    /* ── 제한속도·안전 지점 붙이기 ───────────────────────────────── */

    /**
     * 파싱만으로는 못 채우는 것들을 채운다 —
     *   제한속도는 티맵 도로정보(/tmap/road/nearToRoad)를 구간마다 물어서,
     *   어린이보호구역은 표본 데이터에서 경로 근처 것만 골라서.
     * 네트워크를 타므로 parse() 밖에 둔다. parse() 는 그대로 단위 검증이 된다.
     */
    private NaviRoute enrich(NaviRoute r) {
        double[] cum = cumulative(r.line());

        List<NaviRoute.Section> sections = withSpeedLimits(r.sections(), r.line(), cum);

        List<NaviRoute.Alert> alerts = new ArrayList<>();
        for (NaviRoute.Alert a : r.alerts()) {
            alerts.add(a.speedLimit() != null ? a
                    : new NaviRoute.Alert(a.kind(), a.lat(), a.lng(), a.meterFromStart(),
                            a.name(), speedAt(sections, a.meterFromStart()), a.note()));
        }
        int seedHits = 0;
        for (NaviRoute.Alert a : seedAlerts(r.line(), cum)) {
            alerts.add(a);
            seedHits++;
        }
        alerts.sort((a, b) -> Integer.compare(a.meterFromStart(), b.meterFromStart()));

        return new NaviRoute(r.ready(), r.line(), r.guides(), r.totalMeters(), r.totalSeconds(),
                r.note(), sections, List.copyOf(alerts), alertSource(r.alerts().size(), seedHits));
    }

    /** alerts 가 어디서 온 것인지 정직하게 한 줄. */
    private String alertSource(int facilityCount, int seedCount) {
        if (facilityCount > 0 && seedCount > 0)
            return "터널·교량은 티맵 경로 응답, 어린이보호구역은 " + safetySeed.source();
        if (facilityCount > 0)
            return "티맵 경로 응답의 도로 시설물 정보(터널·교량·지하차도·고가도로)";
        if (seedCount > 0)
            return safetySeed.source();
        return "이 경로에는 알려 줄 지점이 없습니다";
    }

    /** 구간마다 티맵 도로정보를 물어 제한속도를 채운다. 못 받은 구간은 도로 등급으로 추정하거나 비운다. */
    private List<NaviRoute.Section> withSpeedLimits(List<NaviRoute.Section> sections,
                                                    List<double[]> line, double[] cum) {
        if (sections.isEmpty() || line.size() < 2 || !tmapClient.ready()) {
            return sections;
        }
        List<CompletableFuture<Road>> jobs = new ArrayList<>();
        int budget = SPEED_LOOKUP_BUDGET;
        for (NaviRoute.Section s : sections) {
            if (budget <= 0 || s.meters() < SPEED_MIN_SECTION_METERS) {
                jobs.add(CompletableFuture.completedFuture(null));
                continue;
            }
            budget--;
            final boolean second = budget > 0 && s.meters() >= 200;
            if (second) budget--;
            double mid = (s.fromMeter() + s.toMeter()) / 2.0;
            double quarter = s.fromMeter() + s.meters() * 0.25;
            String want = s.roadName();
            jobs.add(CompletableFuture.supplyAsync(() -> {
                Road v = askRoad(line, cum, mid, want);
                if (v == null && second) v = askRoad(line, cum, quarter, want);
                return v;
            }, speedPool));
        }
        try {
            CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0]))
                    .get(6, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[navi] 제한속도 조회가 시간 안에 안 끝났습니다: {}", e.getMessage());
        }

        List<NaviRoute.Section> out = new ArrayList<>(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            NaviRoute.Section s = sections.get(i);
            Road live = null;
            try {
                if (jobs.get(i).isDone()) live = jobs.get(i).getNow(null);
            } catch (Exception ignore) {
                // 한 구간 실패로 경로 전체를 버리지 않는다
            }
            Integer limit = live == null ? null : live.speed();
            Integer lanes = live == null || live.lanes() <= 0 ? null : live.lanes();
            String src = "티맵 도로정보";
            if (limit == null) {
                limit = guessByRoadType(s.roadType());
                src = limit == null ? "없음" : "도로등급 추정";
            }
            out.add(new NaviRoute.Section(s.roadName(), s.roadType(), s.facilityType(),
                    s.meters(), s.seconds(), s.fromMeter(), s.toMeter(), limit, src, lanes));
        }
        return List.copyOf(out);
    }

    /**
     * 경로 위 누적거리 meter 지점의 좌표를 도로에 붙여 제한속도를 받는다.
     * 붙은 도로 이름이 기대한 도로와 다르면 버린다 — 옆 골목에 붙었다는 뜻이고,
     * 그 골목의 30km/h 를 대로 위에 띄우면 운전자가 속는다.
     */
    private Road askRoad(List<double[]> line, double[] cum, double meter, String wantRoad) {
        double[] p = line.get(vertexAt(cum, meter));
        String key = String.format("%.5f,%.5f", p[0], p[1]);
        Road hit = roadCache.get(key);
        if (hit == null) {
            JsonNode res = tmapClient.nearToRoad(p[0], p[1]);
            if (res == null) return null;
            JsonNode h = res.path("resultData").path("header");
            if (h.isMissingNode()) return null;
            int speed = h.path("speed").asInt(0);
            if (speed <= 0) return null;
            /* 차선 수도 같은 응답에 들어 있다. 한 번 물은 값을 버릴 이유가 없다 —
               화면의 차선 안내가 「3차선짜리 그림」이 아니라 실제 차선 수로 그려진다 */
            hit = new Road(speed, h.path("lane").asInt(0), h.path("roadName").asText(""));
            if (roadCache.size() > 5000) roadCache.clear();
            roadCache.put(key, hit);
        }
        return sameRoad(hit.name(), wantRoad) ? hit : null;
    }

    private static boolean sameRoad(String a, String b) {
        return a != null && !a.isBlank() && a.equals(b);
    }

    /**
     * 티맵 도로정보를 못 받았을 때의 마지막 수단.
     * 실제 응답으로 확인한 두 등급만 추정한다 —
     *   roadType 0 은 "… 고속도로 입구 후" 안내 뒤에 나오는 고속도로,
     *   roadType 1 은 "전방 도시고속도로 입구 후" 안내 뒤에 나오는 도시고속도로.
     * 나머지 등급은 무엇인지 확인하지 못했으므로 비워 둔다. 모르는 숫자를 띄우지 않는다.
     */
    private static Integer guessByRoadType(int roadType) {
        if (roadType == 0) return 100;
        if (roadType == 1) return 80;
        return null;
    }

    /**
     * 누적거리 meter 지점이 속한 구간의 제한속도.
     * 구간 경계(앞 구간의 toMeter == 뒤 구간의 fromMeter)에서는 뒤 구간을 쓴다 —
     * 터널 알림은 터널 시작점에 찍히므로 앞 도로의 속도를 가져오면 안 된다.
     */
    private static Integer speedAt(List<NaviRoute.Section> sections, int meter) {
        for (NaviRoute.Section s : sections) {
            if (meter >= s.fromMeter() && meter < s.toMeter()) return s.speedLimit();
        }
        return sections.isEmpty() ? null : sections.get(sections.size() - 1).speedLimit();
    }

    /** 표본 데이터에서 경로선 근처에 있는 것만 골라 온다. */
    private List<NaviRoute.Alert> seedAlerts(List<double[]> line, double[] cum) {
        List<NaviRoute.Alert> out = new ArrayList<>();
        List<SafetySeed.Spot> spots = safetySeed.spots();
        if (spots.isEmpty() || line.isEmpty()) return out;

        // 경로 바깥 것은 먼저 버린다 — 전국 데이터가 들어와도 좌표 비교를 다 돌지 않게
        double minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
        for (double[] p : line) {
            minLat = Math.min(minLat, p[0]); maxLat = Math.max(maxLat, p[0]);
            minLng = Math.min(minLng, p[1]); maxLng = Math.max(maxLng, p[1]);
        }
        double pad = 0.01;   // 약 1km

        for (SafetySeed.Spot s : spots) {
            if (s.lat() < minLat - pad || s.lat() > maxLat + pad
                    || s.lng() < minLng - pad || s.lng() > maxLng + pad) continue;
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < line.size(); i++) {
                double d = meters(s.lat(), s.lng(), line.get(i)[0], line.get(i)[1]);
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best < 0 || bestD > s.radiusMeters()) continue;
            out.add(new NaviRoute.Alert(s.kind(), s.lat(), s.lng(), (int) Math.round(cum[best]),
                    label(s), s.speedLimit(), s.note() == null ? "" : s.note()));
        }
        return out;
    }

    private static String label(SafetySeed.Spot s) {
        if (s.name() == null || s.name().isBlank()) return "";
        return "school".equals(s.kind()) ? s.name() + " 어린이보호구역" : s.name();
    }

    /* ── 파싱 ────────────────────────────────────────────────── */

    /** 원응답 feature 하나 — 구간(LineString)이거나 안내점(Point)이다. */
    private record Feat(boolean isLine, JsonNode props, JsonNode coords) {}

    // GeoJSON FeatureCollection → NaviRoute.
    // LineString feature = 경로 한 구간, Point feature(description 있는 것) = 턴 안내점.
    // 네트워크를 타지 않는다 — 원응답만 주면 그대로 검증할 수 있게.
    static NaviRoute parse(JsonNode res) {
        if (res == null) return null;
        JsonNode features = res.path("features");
        if (!features.isArray() || features.isEmpty()) return null;

        // feature 한 개를 그대로 들고 있는다. 안내점이 「바로 뒤 구간」을 알아야 해서 두 번 훑는다
        List<Feat> feats = new ArrayList<>();
        int totalMeters = 0, totalSeconds = 0;

        for (JsonNode f : features) {
            JsonNode geom = f.path("geometry");
            JsonNode props = f.path("properties");
            String type = geom.path("type").asText("");
            if (!"LineString".equals(type) && !"Point".equals(type)) continue;

            // 전체 거리·시간은 첫 Point(출발점)의 properties 에 담겨 온다
            if (props.has("totalDistance") && totalMeters == 0) totalMeters = props.path("totalDistance").asInt(0);
            if (props.has("totalTime") && totalSeconds == 0) totalSeconds = props.path("totalTime").asInt(0);

            feats.add(new Feat("LineString".equals(type), props, geom.path("coordinates")));
        }
        if (feats.isEmpty()) return null;

        int n = feats.size();
        int[] sectionOf = new int[n];          // 이 feature 가 sections 의 몇 번째인가 (Point 면 -1)
        Arrays.fill(sectionOf, -1);
        int si = 0;
        for (int i = 0; i < n; i++) if (feats.get(i).isLine()) sectionOf[i] = si++;

        // 뒤에서부터 — 각 feature 뒤에 오는 첫 구간과 그 길이
        int[] nextSection = new int[n];
        int[] nextMeters = new int[n];
        int carrySec = -1, carryLen = 0;
        for (int i = n - 1; i >= 0; i--) {
            if (feats.get(i).isLine()) {
                carrySec = sectionOf[i];
                carryLen = feats.get(i).props().path("distance").asInt(0);
            }
            nextSection[i] = carrySec;
            nextMeters[i] = carryLen;
        }

        List<double[]> line = new ArrayList<>();
        List<NaviRoute.Guide> guides = new ArrayList<>();
        List<NaviRoute.Section> sections = new ArrayList<>();
        int cum = 0;

        for (int i = 0; i < n; i++) {
            Feat f = feats.get(i);
            JsonNode p = f.props();
            if (f.isLine()) {
                for (JsonNode c : f.coords()) {
                    // 티맵 좌표는 [경도(x), 위도(y)] 순 → [lat,lng] 로 뒤집는다
                    line.add(new double[]{ c.get(1).asDouble(), c.get(0).asDouble() });
                }
                int d = p.path("distance").asInt(0);
                sections.add(new NaviRoute.Section(
                        p.path("name").asText(""),
                        p.path("roadType").asInt(-1),
                        p.path("facilityType").asInt(0),
                        d,
                        p.path("time").asInt(0),
                        cum, cum + d,
                        null, "없음", null));
                cum += d;
            } else {
                String desc = p.path("description").asText("");
                if (desc.isBlank()) continue;
                JsonNode c = f.coords();
                guides.add(new NaviRoute.Guide(
                        c.get(1).asDouble(), c.get(0).asDouble(),
                        desc,
                        p.path("turnType").asInt(0),
                        p.path("pointType").asText(""),
                        p.path("name").asText(""),
                        p.path("nextRoadName").asText(""),
                        cum,
                        nextMeters[i],
                        nextSection[i]));
            }
        }
        if (line.isEmpty()) return null;

        return new NaviRoute(true, List.copyOf(line), List.copyOf(guides),
                totalMeters, totalSeconds, "지금 교통량 기준입니다.",
                List.copyOf(sections),
                facilityAlerts(feats, sectionOf, sections, line),
                "티맵 경로 응답의 도로 시설물 정보(터널·교량·지하차도·고가도로)");
    }

    /**
     * 터널·교량·지하차도·고가도로 구간을 알림으로 뽑는다.
     * 이름은 그 구간 바로 앞 안내점이 갖고 있다 — "남산3호터널", "이태원 지하차도" 처럼.
     * 같은 시설이 연달아 여러 구간으로 쪼개져 오면 하나로 합친다.
     */
    private static List<NaviRoute.Alert> facilityAlerts(List<Feat> feats, int[] sectionOf,
                                                        List<NaviRoute.Section> sections,
                                                        List<double[]> line) {
        List<NaviRoute.Alert> out = new ArrayList<>();
        if (sections.isEmpty()) return List.of();

        // 구간 -> feature 위치, 그리고 구간 시작 좌표를 찾기 위한 경로선 위치
        int[] featOf = new int[sections.size()];
        int[] lineStartOf = new int[sections.size()];
        int lineCursor = 0;
        for (int i = 0; i < sectionOf.length; i++) {
            if (sectionOf[i] < 0) continue;
            featOf[sectionOf[i]] = i;
            lineStartOf[sectionOf[i]] = lineCursor;
            lineCursor += feats.get(i).coords().size();
        }

        int k = 0;
        while (k < sections.size()) {
            int ft = sections.get(k).facilityType();
            String kind = facilityKind(ft);
            if (kind == null) { k++; continue; }
            int end = k;
            while (end + 1 < sections.size() && sections.get(end + 1).facilityType() == ft) end++;

            NaviRoute.Section head = sections.get(k);
            int meters = sections.get(end).toMeter() - head.fromMeter();
            int at = Math.min(lineStartOf[k], line.size() - 1);
            String name = facilityName(feats, featOf[k]);
            if (name.isBlank()) name = head.roadName();

            out.add(new NaviRoute.Alert(kind, line.get(at)[0], line.get(at)[1],
                    head.fromMeter(), name, null, span(meters)));
            k = end + 1;
        }
        return List.copyOf(out);
    }

    /** 시설물 구간 바로 앞 안내점이 시설 이름을 갖고 있다. 앞이 구간이면 이름이 없다. */
    private static String facilityName(List<Feat> feats, int lineFeatIdx) {
        if (lineFeatIdx <= 0) return "";
        Feat prev = feats.get(lineFeatIdx - 1);
        if (prev.isLine()) return "";
        return prev.props().path("name").asText("");
    }

    private static String facilityKind(int facilityType) {
        return switch (facilityType) {
            case FACILITY_BRIDGE -> "bridge";
            case FACILITY_TUNNEL -> "tunnel";
            case FACILITY_OVERPASS -> "overpass";
            case FACILITY_UNDERPASS -> "underpass";
            default -> null;   // 확인하지 못한 코드는 내보내지 않는다
        };
    }

    private static String span(int meters) {
        if (meters <= 0) return "";
        return meters >= 1000
                ? String.format("%.1fkm 이어집니다", meters / 1000.0)
                : meters + "m 이어집니다";
    }

    /* ── 거리 계산 ───────────────────────────────────────────── */

    /** 경로선 각 점까지의 누적 거리(m). */
    private static double[] cumulative(List<double[]> line) {
        double[] cum = new double[line.size()];
        for (int i = 1; i < line.size(); i++) {
            cum[i] = cum[i - 1] + meters(line.get(i - 1)[0], line.get(i - 1)[1],
                    line.get(i)[0], line.get(i)[1]);
        }
        return cum;
    }

    /** 누적거리 meter 에 가장 가까운 경로선 점의 위치. */
    private static int vertexAt(double[] cum, double meter) {
        if (cum.length == 0) return 0;
        int lo = 0, hi = cum.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] < meter) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    private static double meters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /* ── 경유지 최적화 · 주변 검색 (그대로) ─────────────────────── */

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

    /* ── 자체 검증 ───────────────────────────────────────────── */

    // 파싱이 깨지면 실패. 티맵 실호출 없이 원응답 모양만으로 돈다.
    public static void main(String[] args) throws Exception {
        boolean assertsOn = false;
        assert assertsOn = true;
        if (!assertsOn) throw new IllegalStateException("-ea 로 실행해야 검증이 됩니다");

        String sample = "{\"features\":["
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.0413,35.1151]},"
                + "\"properties\":{\"totalDistance\":14043,\"totalTime\":2760,\"pointType\":\"S\","
                + "\"turnType\":200,\"name\":\"\",\"nextRoadName\":\"일반도로\",\"description\":\"출발\"}},"
                + "{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[129.0413,35.1151],[129.05,35.12]]},"
                + "\"properties\":{\"name\":\"수영로\",\"distance\":900,\"time\":120,\"roadType\":6,\"facilityType\":0}},"
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.05,35.12]},"
                + "\"properties\":{\"turnType\":121,\"pointType\":\"N\",\"name\":\"황령터널\","
                + "\"nextRoadName\":\"황령대로\",\"description\":\"황령터널에서 터널 후 황령대로를 따라 1936m 이동\"}},"
                + "{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[129.05,35.12],[129.06,35.13]]},"
                + "\"properties\":{\"name\":\"황령대로\",\"distance\":1936,\"time\":140,\"roadType\":6,\"facilityType\":2}},"
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[129.1604,35.1587]},"
                + "\"properties\":{\"turnType\":13,\"pointType\":\"E\",\"name\":\"해운대 교차로\","
                + "\"nextRoadName\":\"\",\"description\":\"해운대로 방면으로 우회전\"}}"
                + "]}";
        JsonNode node = new ObjectMapper().readTree(sample);
        NaviRoute r = parse(node);

        // 원래 있던 것 — 깨지면 프런트가 죽는다
        assert r != null && r.ready() : "파싱 성공해야";
        assert r.line().size() == 4 : "경로선 4점: " + r.line().size();
        assert r.line().get(0)[0] == 35.1151 && r.line().get(0)[1] == 129.0413 : "lat/lng 순서 뒤집기";
        assert r.guides().size() == 3 : "턴안내 3개(description 있는 것만): " + r.guides().size();
        assert r.totalMeters() == 14043 : "전체거리";
        assert r.guides().get(2).description().contains("우회전") : "턴 문구";

        // 새로 넣은 것 — 안내점
        NaviRoute.Guide g0 = r.guides().get(0), g1 = r.guides().get(1), g2 = r.guides().get(2);
        assert g0.meterFromStart() == 0 : "출발점 누적거리 0: " + g0.meterFromStart();
        assert g0.legMeters() == 900 : "출발 뒤 구간 900m: " + g0.legMeters();
        assert g0.sectionIdx() == 0 : "출발 뒤 구간은 0번: " + g0.sectionIdx();
        assert "일반도로".equals(g0.nextRoadName()) : "nextRoadName: " + g0.nextRoadName();
        assert "황령터널".equals(g1.name()) : "교차로 이름: " + g1.name();
        assert g1.meterFromStart() == 900 : "두 번째 안내점 누적 900m: " + g1.meterFromStart();
        assert g1.legMeters() == 1936 && g1.sectionIdx() == 1 : "두 번째 안내 뒤 구간";
        assert g2.sectionIdx() == -1 && g2.legMeters() == 0 : "도착 뒤에는 구간이 없다";

        // 새로 넣은 것 — 구간
        assert r.sections().size() == 2 : "구간 2개: " + r.sections().size();
        NaviRoute.Section s0 = r.sections().get(0), s1 = r.sections().get(1);
        assert "수영로".equals(s0.roadName()) && s0.meters() == 900 && s0.seconds() == 120 : "구간0";
        assert s0.fromMeter() == 0 && s0.toMeter() == 900 : "구간0 누적구간";
        assert s1.fromMeter() == 900 && s1.toMeter() == 2836 : "구간1 누적구간: " + s1.toMeter();
        assert s1.facilityType() == 2 : "구간1 시설물 코드";
        assert s0.speedLimit() == null && "없음".equals(s0.speedSource())
                : "파싱 단계에서는 제한속도를 모른다 — 티맵 도로정보를 따로 불러야 채워진다";
        assert s0.lanes() == null : "차선 수도 도로정보를 불러야 채워진다";

        // 새로 넣은 것 — 알림
        assert r.alerts().size() == 1 : "터널 알림 1개: " + r.alerts().size();
        NaviRoute.Alert a = r.alerts().get(0);
        assert "tunnel".equals(a.kind()) : "터널로 분류: " + a.kind();
        assert "황령터널".equals(a.name()) : "앞 안내점에서 이름을 가져온다: " + a.name();
        assert a.meterFromStart() == 900 : "터널 시작 누적거리: " + a.meterFromStart();
        assert a.lat() == 35.12 && a.lng() == 129.05 : "터널 시작 좌표";
        assert a.note().contains("1.9km") : "구간 길이 한 줄: " + a.note();
        assert r.alertSource().contains("티맵") : "출처";

        // 확인 못 한 시설물 코드는 내보내지 않는다 (5 는 무엇인지 확인하지 못했다)
        String unknownFacility = sample.replace("\"facilityType\":2", "\"facilityType\":5");
        NaviRoute u = parse(new ObjectMapper().readTree(unknownFacility));
        assert u != null && u.alerts().isEmpty() : "확인 못 한 코드로는 알림을 만들지 않는다";

        // 시설물 구간이 연달아 오면 하나로 합친다
        String twoLegs = "{\"features\":["
                + "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[126.5,37.4]},"
                + "\"properties\":{\"totalDistance\":2000,\"totalTime\":100,\"pointType\":\"S\",\"description\":\"출발\"}},"
                + "{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[126.5,37.4],[126.51,37.41]]},"
                + "\"properties\":{\"name\":\"인천대교 고속도로\",\"distance\":1000,\"time\":40,\"roadType\":0,\"facilityType\":1}},"
                + "{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[126.51,37.41],[126.52,37.42]]},"
                + "\"properties\":{\"name\":\"인천대교 고속도로\",\"distance\":600,\"time\":25,\"roadType\":0,\"facilityType\":1}}"
                + "]}";
        NaviRoute t = parse(new ObjectMapper().readTree(twoLegs));
        assert t != null && t.alerts().size() == 1 : "붙어 있는 교량 구간은 하나로: " + t.alerts().size();
        assert "bridge".equals(t.alerts().get(0).kind()) : "교량으로 분류";
        assert t.alerts().get(0).note().contains("1.6km") : "합친 길이: " + t.alerts().get(0).note();
        assert "인천대교 고속도로".equals(t.alerts().get(0).name()) : "앞 안내점이 없으면 도로 이름";
        assert guessByRoadType(0) == 100 && guessByRoadType(1) == 80 && guessByRoadType(6) == null
                : "확인한 도로 등급만 추정한다";

        // 구간 경계에서는 뒤 구간의 속도를 쓴다 — 터널 알림에 터널 앞 도로의 속도가 붙으면 안 된다
        List<NaviRoute.Section> two = List.of(
                new NaviRoute.Section("앞길", 6, 0, 900, 60, 0, 900, 80, "티맵 도로정보", 3),
                new NaviRoute.Section("터널길", 6, 2, 1000, 60, 900, 1900, 50, "티맵 도로정보", 2));
        assert speedAt(two, 0) == 80 : "구간0 안";
        assert speedAt(two, 900) == 50 : "경계 900m 는 뒤 구간: " + speedAt(two, 900);
        assert speedAt(two, 1900) == 50 : "끝점은 마지막 구간";

        // 누적거리 계산 — 경로선 위 한 점 찾기
        double[] cum = cumulative(r.line());
        assert cum[0] == 0 && cum[cum.length - 1] > 0 : "누적거리";
        assert vertexAt(cum, 0) == 0 : "0m 는 첫 점";

        System.out.println("OK 네비 파싱 정상 line=" + r.line().size()
                + " guides=" + r.guides().size()
                + " sections=" + r.sections().size()
                + " alerts=" + r.alerts().size());

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
