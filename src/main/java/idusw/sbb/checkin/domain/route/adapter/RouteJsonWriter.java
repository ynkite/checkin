package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.route.engine.Anchor;
import idusw.sbb.checkin.domain.route.engine.Candidate;
import idusw.sbb.checkin.domain.route.engine.DayPlan;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.route.engine.Haversine;
import idusw.sbb.checkin.domain.route.engine.RouteConstraints;
import idusw.sbb.checkin.domain.route.engine.SlotOptimizer;
import idusw.sbb.checkin.domain.route.engine.SlotPlan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleBiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엔진의 {@link DayPlan} 들을 프론트가 읽는 최종 JSON 으로 옮기는 경계 어댑터 (설계 결정 10-3·10-5).
 *
 * <p>채우는 건 장소 10개 필드({@code type icon name sub stars key time replacePh lat lng})와
 * 일차 4개({@code day places budget label}) 뿐이다. {@code isFound}·{@code pathCoords}·transit 문구는
 * {@code saveAiRouteToDb → recalcTransitWithKakao} 가 저장 직전에 심으므로 여기서 건드리지 않는다.
 *
 * <p><b>다만 {@code {"transit":"이동"}} 플레이스홀더 노드는 여기서 끼워 넣어야 한다.</b>
 * {@code recalcTransitWithKakao} 는 이미 있는 transit 노드의 문구만 채울 뿐 없는 노드를 만들지 않는다
 * — 플레이스홀더가 없으면 거리·경로 폴리라인이 통째로 빠진다.
 *
 * <p>숙소는 엔진 입력에서 빠지지만 출력에는 되돌아온다 (결정 2·8). {@code page_map.html} 477행이
 * {@code type === 'stay'} 로 숙박비를 집계하고 597행이 숙소 행을 그린다. 배치 규칙은 기존 출력과 같다.
 *
 * <ul>
 *   <li>첫날 — 끝에만 (체크인)</li>
 *   <li>중간 날 — 시작과 끝 (출발·취침)</li>
 *   <li>마지막 날 — 시작에만 (체크아웃)</li>
 *   <li>당일치기 — 없음</li>
 * </ul>
 */
public final class RouteJsonWriter {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DAY_OF_WEEK_KO = {"월", "화", "수", "목", "금", "토", "일"};
    private static final String REPLACE_PLACEHOLDER = "장소 교체 요청";
    private static final String STARS_UNKNOWN = "평점 정보 없음";
    private static final String STAY_TYPE = "stay";
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("₩([\\d,]+)(?:×(\\d+))?");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CandidateAdapter candidateAdapter;
    private final ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes;

    /**
     * @param travelTimeMinutes 방문 시각을 되짚는 데 쓴다 — <b>{@code DayPlanner} 에 준 것과 같은
     *                          함수여야 한다.</b> 다르면 화면에 찍히는 시각이 엔진이 검증한 시각과 어긋난다
     */
    public RouteJsonWriter(CandidateAdapter candidateAdapter,
                           ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes) {
        if (candidateAdapter == null) {
            throw new IllegalArgumentException("candidateAdapter must not be null");
        }
        if (travelTimeMinutes == null) {
            throw new IllegalArgumentException("travelTimeMinutes must not be null");
        }
        this.candidateAdapter = candidateAdapter;
        this.travelTimeMinutes = travelTimeMinutes;
    }

    /** {@code DayPlanner.withDefaults()} 와 같은 비용 함수 (Haversine · 시속 30km). */
    public static RouteJsonWriter withDefaults(CandidateAdapter candidateAdapter) {
        return new RouteJsonWriter(candidateAdapter,
                (from, to) -> Haversine.distanceKm(from, to) / SlotOptimizer.DEFAULT_AVERAGE_SPEED_KMH * 60.0);
    }

    /**
     * @param anchor    숙소. 당일치기이거나 못 잡았으면 null — 그러면 stay 행이 하나도 안 붙는다
     * @param startDate 라벨의 날짜. null 이면 라벨에서 날짜를 뺀다
     */
    public ArrayNode write(List<DayPlan> dayPlans, Anchor anchor, RouteConstraints constraints, LocalDate startDate) {
        if (dayPlans == null) {
            throw new IllegalArgumentException("dayPlans must not be null");
        }
        if (constraints == null) {
            throw new IllegalArgumentException("constraints must not be null");
        }

        ArrayNode root = objectMapper.createArrayNode();
        for (int i = 0; i < dayPlans.size(); i++) {
            root.add(writeDay(dayPlans.get(i), anchor, constraints, startDate, i == 0, i == dayPlans.size() - 1));
        }
        return root;
    }

    public String writeJson(List<DayPlan> dayPlans, Anchor anchor, RouteConstraints constraints, LocalDate startDate) {
        try {
            return objectMapper.writeValueAsString(write(dayPlans, anchor, constraints, startDate));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("동선 JSON 직렬화 실패", e);
        }
    }

    private ObjectNode writeDay(DayPlan dayPlan, Anchor anchor, RouteConstraints constraints,
                                LocalDate startDate, boolean isFirstDay, boolean isLastDay) {
        int dayNumber = dayPlan.dayIndex() + 1;

        List<ObjectNode> rows = new ArrayList<>();
        if (anchor != null && !isFirstDay) {
            rows.add(stayRow(anchor, constraints.effectiveDayStart(false)));
        }
        for (SlotPlan slotPlan : dayPlan.slotPlans()) {
            rows.addAll(placeRows(slotPlan));
        }
        if (anchor != null && !isLastDay) {
            rows.add(stayRow(anchor, dayPlan.returnTime()));
        }

        long budget = 0;
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("key", "d" + dayNumber + "_" + (i + 1));
            budget += parseAmount(rows.get(i).path("sub").asText(""));
        }

        ObjectNode dayNode = objectMapper.createObjectNode();
        dayNode.put("day", dayNumber);
        dayNode.put("label", label(dayNumber, startDate));
        dayNode.put("budget", String.format("₩%,d", budget));
        dayNode.set("places", interleaveTransit(rows));
        return dayNode;
    }

    private List<ObjectNode> placeRows(SlotPlan slotPlan) {
        List<Candidate> visits = slotPlan.visitOrder();
        List<LocalTime> arrivals = arrivalTimes(slotPlan);
        List<ObjectNode> rows = new ArrayList<>(visits.size());
        for (int i = 0; i < visits.size(); i++) {
            rows.add(placeRow(visits.get(i), arrivals.get(i)));
        }
        return rows;
    }

    /**
     * 방문별 도착 시각을 슬롯 종료 시각에서 거꾸로 접어 복원한다.
     *
     * <p>{@code SlotPlan} 은 종료 시각 하나만 남기지만, {@code SlotOptimizer} 의 전진 시뮬레이션이
     * {@code 도착(i+1) = 도착(i) + 체류(i) + ceil(이동(i→i+1))} 이라 역산이 정확히 성립한다.
     * 창이 열리기 전 도착해 기다린 시간도 종료 시각에 이미 반영돼 있어 창 정보가 필요 없다.
     */
    private List<LocalTime> arrivalTimes(SlotPlan slotPlan) {
        List<Candidate> visits = slotPlan.visitOrder();
        LocalTime[] arrivals = new LocalTime[visits.size()];
        for (int i = visits.size() - 1; i >= 0; i--) {
            LocalTime departure = (i == visits.size() - 1)
                    ? slotPlan.endTime()
                    : arrivals[i + 1].minusMinutes(legMinutes(visits.get(i).location(), visits.get(i + 1).location()));
            arrivals[i] = departure.minusMinutes(visits.get(i).dwellMinutes());
        }
        return List.of(arrivals);
    }

    private long legMinutes(GeoPoint from, GeoPoint to) {
        return (long) Math.ceil(travelTimeMinutes.applyAsDouble(from, to));
    }

    private ObjectNode placeRow(Candidate candidate, LocalTime time) {
        ObjectNode origin = candidateAdapter.originOf(candidate.id());
        String type = CandidateAdapter.jsonType(candidate.category());

        ObjectNode row = objectMapper.createObjectNode();
        row.put("type", type);
        row.put("icon", icon(type));
        row.put("name", candidate.name());
        row.put("sub", text(origin, "sub", ""));
        row.put("stars", text(origin, "stars", STARS_UNKNOWN));
        row.put("time", time.format(TIME_FORMAT));
        row.put("replacePh", REPLACE_PLACEHOLDER);
        row.put("lat", candidate.location().latitude());
        row.put("lng", candidate.location().longitude());
        return row;
    }

    private ObjectNode stayRow(Anchor anchor, LocalTime time) {
        ObjectNode origin = candidateAdapter.originOf(anchor.id());

        ObjectNode row = objectMapper.createObjectNode();
        row.put("type", STAY_TYPE);
        row.put("icon", icon(STAY_TYPE));
        row.put("name", anchor.name());
        row.put("sub", text(origin, "sub", ""));
        row.put("stars", text(origin, "stars", STARS_UNKNOWN));
        row.put("time", time.format(TIME_FORMAT));
        row.put("replacePh", REPLACE_PLACEHOLDER);
        row.put("lat", anchor.location().latitude());
        row.put("lng", anchor.location().longitude());
        return row;
    }

    private ArrayNode interleaveTransit(List<ObjectNode> rows) {
        ArrayNode places = objectMapper.createArrayNode();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                places.add(objectMapper.createObjectNode().put("transit", "이동"));
            }
            places.add(rows.get(i));
        }
        return places;
    }

    private static String label(int dayNumber, LocalDate startDate) {
        if (startDate == null) {
            return String.format("📅 Day %d", dayNumber);
        }
        LocalDate date = startDate.plusDays(dayNumber - 1L);
        return String.format("📅 Day %d · %02d/%02d (%s)",
                dayNumber, date.getMonthValue(), date.getDayOfMonth(),
                DAY_OF_WEEK_KO[date.getDayOfWeek().getValue() - 1]);
    }

    private static String icon(String type) {
        return switch (type) {
            case STAY_TYPE -> "🏨";
            case "food" -> "🍽️";
            case "cafe" -> "☕";
            default -> "📍";
        };
    }

    private static String text(ObjectNode origin, String field, String fallback) {
        if (origin == null) {
            return fallback;
        }
        String value = origin.path(field).asText("").trim();
        return value.isEmpty() ? fallback : value;
    }

    /** {@code "맛집 · 점심 · ₩12,000×2"} → 24000. 서비스의 예상비용 합산과 같은 규칙이다. */
    private static long parseAmount(String sub) {
        Matcher matcher = AMOUNT_PATTERN.matcher(sub);
        if (!matcher.find()) {
            return 0L;
        }
        long amount = Long.parseLong(matcher.group(1).replace(",", ""));
        if (matcher.group(2) != null) {
            amount *= Long.parseLong(matcher.group(2));
        }
        return amount;
    }
}
