package idusw.sbb.checkin.domain.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.dto.RelatedSpot;
import idusw.sbb.checkin.domain.tour.service.RelatedTourService.Status;
import idusw.sbb.checkin.domain.tour.service.RelatedTourService.Suggestion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 챗봇 시스템 프롬프트의 [지금] 절 — 현재 시각, 여행 단계, 위치, 그날 일정, 연관 관광지.
 * 취향 입력(PlanInputForm)이 없는 플랜에도 붙는다. 시드 플랜은 form 이 비어 있다.
 */
final class TripContextPrompt {

    enum Phase { BEFORE, DURING, AFTER }

    /** 일정에서 뽑은 장소 하나. lat·lng 가 없으면 null. */
    record Stop(int day, String time, String name, String type, Double lat, Double lng) {}

    private static final int MAX_STOPS = 12;

    private TripContextPrompt() {}

    static Phase phase(LocalDate today, LocalDate start, LocalDate end) {
        if (start == null) return Phase.BEFORE;
        LocalDate last = end != null ? end : start;
        if (today.isBefore(start)) return Phase.BEFORE;
        if (today.isAfter(last)) return Phase.AFTER;
        return Phase.DURING;
    }

    /** routeJson(draft 가 있으면 draft) 을 장소 목록으로. 못 읽으면 빈 목록. 이동 구간(transit)은 뺀다. */
    static List<Stop> stops(ObjectMapper om, String routeJson) {
        List<Stop> out = new ArrayList<>();
        if (routeJson == null || routeJson.isBlank()) return out;
        try {
            JsonNode root = om.readTree(routeJson);
            if (!root.isArray()) return out;
            for (JsonNode day : root) {
                int d = day.path("day").asInt(0);
                for (JsonNode p : day.path("places")) {
                    if (p.hasNonNull("transit")) continue;
                    String name = p.path("name").asText("").trim();
                    if (name.isEmpty()) continue;
                    Double lat = p.hasNonNull("lat") ? p.path("lat").asDouble() : null;
                    Double lng = p.hasNonNull("lng") ? p.path("lng").asDouble() : null;
                    if (lat != null && lng != null && lat == 0.0 && lng == 0.0) { lat = null; lng = null; }
                    out.add(new Stop(d, p.path("time").asText(""), name, p.path("type").asText(""), lat, lng));
                }
            }
        } catch (Exception ignored) {
            // 깨진 JSON 은 일정이 없는 것과 같게 다룬다
        }
        return out;
    }

    /** 여행 중이면 오늘, 여행 전이면 첫날, 끝났으면 빈 목록. */
    static int focusDay(Phase phase, LocalDate today, LocalDate start) {
        return switch (phase) {
            case DURING -> (int) ChronoUnit.DAYS.between(start, today) + 1;
            case BEFORE -> 1;
            case AFTER -> 0;
        };
    }

    static List<Stop> dayStops(List<Stop> all, int day) {
        return all.stream().filter(s -> s.day() == day).limit(MAX_STOPS).toList();
    }

    static String build(ZonedDateTime now, String destination, LocalDate start, LocalDate end,
                        List<Stop> all, Double lat, Double lng, Suggestion related) {
        return build(now, destination, start, end, all, lat, lng, related, null);
    }

    /**
     * 날씨 한 줄. 실시간 화면(LiveService)이 쓰는 것과 같은 값을 받는다 —
     * 키: sky · rainProb · source(SHORT|MID|NORMAL) · outdoorRisk. 비었으면 「받지 못함」이라고 적는다.
     */
    static String weatherLine(java.util.Map<String, Object> w) {
        if (w == null || w.isEmpty()) {
            return "- 날씨: 예보를 받지 못함. 날씨를 지어내지 말고 확인되지 않았다고 말하세요.\n";
        }
        String src = String.valueOf(w.getOrDefault("source", ""));
        if ("NORMAL".equals(src)) {
            return "- 날씨: 아직 기상청 예보가 없는 날입니다. 비 여부는 확인되지 않았습니다.\n";
        }
        String label = "SHORT".equals(src) ? "기상청 단기예보" : "MID".equals(src) ? "기상청 중기예보" : "기상청 예보";
        StringBuilder sb = new StringBuilder("- 날씨(" + label + ", 예보이지 관측값이 아님): ");
        Object sky = w.get("sky") != null ? w.get("sky") : w.get("label");
        if (sky != null) sb.append(sky).append(", ");
        Object rp = w.get("rainProb");
        sb.append(rp instanceof Number n ? "비 올 확률 " + n.intValue() + "%" : "비 올 확률은 확인되지 않음");
        if (Boolean.TRUE.equals(w.get("outdoorRisk"))) sb.append(". 다음 장소가 야외인데 비 예보가 있음");
        return sb.append('\n').toString();
    }

    static String build(ZonedDateTime now, String destination, LocalDate start, LocalDate end,
                        List<Stop> all, Double lat, Double lng, Suggestion related,
                        java.util.Map<String, Object> weather) {
        LocalDate today = now.toLocalDate();
        Phase phase = phase(today, start, end);
        int day = focusDay(phase, today, start);
        List<Stop> focus = dayStops(all, day);

        StringBuilder sb = new StringBuilder("\n[지금 — 서버가 넣은 사실. 추측하지 말고 이 값을 쓰세요]\n");
        sb.append("- 현재 시각: ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
          .append('(').append(now.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN)).append(") ")
          .append(now.format(DateTimeFormatter.ofPattern("HH:mm"))).append(" 한국 시각\n");
        sb.append("- 여행지: ").append(nz(destination)).append('\n');
        if (start != null) {
            sb.append("- 여행 기간: ").append(start).append(" ~ ").append(end != null ? end : start).append('\n');
        }
        switch (phase) {
            case BEFORE -> sb.append("- 여행 단계: 출발 전 (출발까지 ")
                    .append(ChronoUnit.DAYS.between(today, start)).append("일)\n");
            case DURING -> sb.append("- 여행 단계: 여행 중, 오늘은 ").append(day).append("일차\n");
            case AFTER -> sb.append("- 여행 단계: 여행이 끝났음\n");
        }

        if (phase != Phase.AFTER) sb.append(weatherLine(weather));

        /* 위치 */
        if (lat != null && lng != null) {
            sb.append(String.format(Locale.ROOT, "- 사용자 현재 위치(기기 위치): 위도 %.5f, 경도 %.5f%n", lat, lng));
            Stop near = nearest(focus.isEmpty() ? all : focus, lat, lng);
            if (near != null) {
                sb.append(String.format(Locale.ROOT, "- 일정 중 가장 가까운 곳: %s (직선 약 %.1fkm)%n",
                        near.name(), km(lat, lng, near.lat(), near.lng())));
            }
        } else {
            sb.append("- 사용자 기기 위치: 받지 못함. 위치를 물으면 아래 일정 좌표를 기준으로 답하고, 실제 위치는 모른다고 말하세요.\n");
        }

        /* 그날 일정과 좌표 */
        if (!focus.isEmpty()) {
            sb.append("- ").append(phase == Phase.DURING ? "오늘" : day + "일차").append(" 일정 (시각 · 장소 · 종류 · 좌표):\n");
            LocalTime nowTime = now.toLocalTime();
            boolean marked = false;
            for (Stop s : focus) {
                String mark = "";
                if (phase == Phase.DURING && !marked && isAfter(s.time(), nowTime)) { mark = "  <- 다음 장소"; marked = true; }
                sb.append("  ").append(s.time().isBlank() ? "--:--" : s.time()).append(" · ").append(s.name())
                  .append(" · ").append(s.type().isBlank() ? "기타" : s.type()).append(" · ")
                  .append(s.lat() != null ? String.format(Locale.ROOT, "%.5f,%.5f", s.lat(), s.lng()) : "좌표 없음")
                  .append(mark).append('\n');
            }
        } else if (phase != Phase.AFTER) {
            sb.append("- 저장된 일정(경로)이 아직 없음. 일정 속 장소를 지어내지 마세요.\n");
        }

        /* 연관 관광지 */
        sb.append(relatedSection(related));
        sb.append("""

                [길 안내 규칙]
                - 대중교통 노선·환승역·걸리는 시간은 위에 적힌 값이 없으면 말하지 마세요. 틀린 노선을 알려 주면 사람이 길을 잃습니다.
                  「지도 앱에서 지금 경로를 확인해 보세요」라고 안내하세요.
                """);
        return sb.toString();
    }

    static String relatedSection(Suggestion r) {
        StringBuilder sb = new StringBuilder();
        if (r == null || r.status() != Status.OK || r.spots().isEmpty()) {
            sb.append("\n[연관 관광지] 이번에는 받지 못했습니다");
            if (r != null && r.status() == Status.NO_AREA) sb.append(" (이 여행지는 지역 코드가 없음)");
            sb.append(". 관광공사 통계를 근거로 댄 추천을 하지 마세요.\n");
            return sb.toString();
        }
        String ym = r.baseYm() != null && r.baseYm().length() == 6
                ? r.baseYm().substring(0, 4) + "년 " + Integer.parseInt(r.baseYm().substring(4)) + "월" : "최근";
        sb.append("\n[연관 관광지 — 한국관광공사 연관 관광지 정보, ").append(r.areaName()).append(", ").append(ym).append(" 통계]\n");
        sb.append(r.basedOn() != null
                ? "'" + r.basedOn() + "'에 다녀간 사람들이 함께 많이 간 곳 (순위순):\n"
                : "이 지역에서 여러 관광지와 함께 가장 자주 묶인 곳:\n");
        for (RelatedSpot s : r.spots()) {
            sb.append("  - ").append(s.toName()).append(" (").append(nz(s.category())).append(")\n");
        }
        sb.append("""
                이 목록 사용 규칙:
                - 주변에 갈 곳·먹을 곳을 추천할 때 이 목록을 먼저 쓰고, 관광공사 통계에서 나온 곳이라고 한 번 밝히세요.
                - 목록 머리에 적힌 기준을 그대로 말하세요. 기준 장소가 적혀 있지 않으면 「어느 장소와 함께 간 곳」이라고 말하지 마세요.
                - 이 목록에는 좌표와 영업시간이 없습니다. 거리·영업 여부를 지어내지 마세요.
                """);
        return sb.toString();
    }

    private static boolean isAfter(String hhmm, LocalTime now) {
        try {
            return LocalTime.parse(hhmm.length() == 4 ? "0" + hhmm : hhmm).isAfter(now);
        } catch (Exception e) {
            return false;
        }
    }

    private static Stop nearest(List<Stop> stops, double lat, double lng) {
        Stop best = null;
        double bestKm = Double.MAX_VALUE;
        for (Stop s : stops) {
            if (s.lat() == null) continue;
            double d = km(lat, lng, s.lat(), s.lng());
            if (d < bestKm) { bestKm = d; best = s; }
        }
        return best;
    }

    static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "확인되지 않음" : s;
    }
}
