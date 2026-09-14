package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.route.engine.Anchor;
import idusw.sbb.checkin.domain.route.engine.Candidate;
import idusw.sbb.checkin.domain.route.engine.DayPlan;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.route.engine.RouteConstraints;
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
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("₩([\\d,]+)(?:×(\\d+))?");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CandidateAdapter candidateAdapter;
    private final ToDoubleBiFunction<GeoPoint, GeoPoint> travelTimeMinutes;

    /**
     * 직접 부르지 말고 {@link RouteEngineFactory#jsonWriter(CandidateAdapter)} 로 받는다.
     * {@code DayPlanner} 와 <b>같은</b> 이동비용 함수를 써야 시각이 어긋나지 않는다.
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
     *
     * <p><b>전제 — 슬롯 내부에는 대기가 없다.</b> 지금 대기는 슬롯의 첫 방문이 창 시작을 기다리는
     * 한 번뿐이고, 그건 종료 시각에 접혀 있어 역산에 걸리지 않는다. 두 번째 방문부터 대기가 생기면
     * (예: 영업 시작 전 도착해 기다리기) 그 시간이 이동시간으로 둔갑해 앞쪽 시각이 전부 당겨진다.
     * {@code SlotOptimizer.simulate} 에 대기를 추가할 일이 생기면 여기도 같이 고쳐야 한다.
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
        return row(candidateAdapter.originOf(candidate.id()),
                CandidateAdapter.jsonType(candidate.category()),
                candidate.name(), candidate.location(), time);
    }

    private ObjectNode stayRow(Anchor anchor, LocalTime time) {
        return row(candidateAdapter.originOf(anchor.id()),
                STAY_TYPE, anchor.name(), anchor.location(), time);
    }

    /**
     * {@code sub}·{@code stars}·{@code icon}·{@code name} 은 원본 후보 노드 값을 <b>그대로</b> 통과시킨다.
     *
     * <p>{@code sub} 에 금액({@code ₩35,000×4})과 끼니 라벨(점심/저녁)이 같이 들어 있어서다. 여기서
     * 새로 지어내면 {@code parseAndSaveEstimatedExpenses} 의 예산 집계와 {@code syncMealLabelByTime}
     * 의 라벨 보정이 동시에 헛돈다 — 둘 다 이 문자열을 읽는다.
     */
    private ObjectNode row(ObjectNode origin, String type, String fallbackName, GeoPoint location, LocalTime time) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("type", type);
        row.put("icon", passThrough(origin, "icon", defaultIcon(type)));
        row.put("name", passThrough(origin, "name", fallbackName));
        row.put("sub", passThrough(origin, "sub", ""));
        row.put("stars", passThrough(origin, "stars", STARS_UNKNOWN));
        row.put("time", displayTime(time).format(TIME_FORMAT));
        row.put("replacePh", REPLACE_PLACEHOLDER);
        row.put("lat", location.latitude());
        row.put("lng", location.longitude());
        return row;
    }

    /**
     * 표시용으로만 5분 단위 반올림한다. 시뮬레이션·검증은 분 단위 그대로다 — 09:03 을 그대로 찍으면
     * 사람이 짠 일정으로 안 보인다.
     */
    private static LocalTime displayTime(LocalTime time) {
        int minutes = time.getHour() * 60 + time.getMinute();
        int rounded = (minutes + 2) / 5 * 5;
        return LocalTime.ofSecondOfDay((rounded % MINUTES_PER_DAY) * 60L);
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

    /** 카카오 후보 노드에는 icon 이 없다 — 원본에 있으면 그게 우선이고, 없을 때만 이 값을 쓴다. */
    private static String defaultIcon(String type) {
        return switch (type) {
            case STAY_TYPE -> "🏨";
            case "food" -> "🍽️";
            case "cafe" -> "☕";
            default -> "📍";
        };
    }

    /** 원본 값을 다듬지 않고 그대로 준다. 비어 있을 때만 fallback. */
    private static String passThrough(ObjectNode origin, String field, String fallback) {
        if (origin == null || !origin.hasNonNull(field)) {
            return fallback;
        }
        String value = origin.path(field).asText("");
        return value.isBlank() ? fallback : value;
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
