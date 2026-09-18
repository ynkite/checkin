package idusw.sbb.checkin.domain.route.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.route.engine.Anchor;
import idusw.sbb.checkin.domain.route.engine.BandSplitter;
import idusw.sbb.checkin.domain.route.engine.Candidate;
import idusw.sbb.checkin.domain.route.engine.DailyCandidatePool;
import idusw.sbb.checkin.domain.route.engine.DailyCategoryBudget;
import idusw.sbb.checkin.domain.route.engine.DayPlan;
import idusw.sbb.checkin.domain.route.engine.DayPlanner;
import idusw.sbb.checkin.domain.route.engine.GeoPoint;
import idusw.sbb.checkin.domain.route.engine.RouteConstraints;
import idusw.sbb.checkin.domain.route.engine.ScheduleDensity;
import idusw.sbb.checkin.domain.route.engine.SlotBuilder;
import idusw.sbb.checkin.domain.route.engine.TimeSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 카카오 후보 → 엔진 → 최종 JSON 을 한 줄로 잇는 조립기 (작업 6). {@code buildRouteWithAI} 가
 * 반환하던 자리에 들어가므로, 출력은 {@code saveAiRouteToDb} 체인이 그대로 받아 간다.
 *
 * <p>가격({@code sub})·평점은 {@code collectCandidatesFromKakao} 가 이미 붙여 둔 값을 그대로
 * 통과시킨다 — 예산 집계와 끼니 라벨 보정이 그 문자열을 읽기 때문이다.
 */
public final class RouteEngineAssembler {

    private static final Logger log = LoggerFactory.getLogger(RouteEngineAssembler.class);

    /** 챗봇이 {@code extraNotes} 에 남기는 태그. 값의 콜론이 안 깨지는 형태다 (app_main.js 와 같은 식). */
    private static final Pattern EXTRA_TAG = Pattern.compile("\\[EXTRA:([^:\\]]+):([^\\]]+)\\]");
    private static final String FIRST_DAY_ARRIVAL_LABEL = "첫날도착";
    private static final String LAST_DAY_DEPARTURE_LABEL = "마지막날출발";

    private static final LocalTime CAR_ARRIVAL = LocalTime.of(11, 0);
    private static final LocalTime TRANSIT_ARRIVAL = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);
    private static final LocalTime DINNER_START = LocalTime.of(18, 0);
    private static final LocalTime DINNER_END = LocalTime.of(19, 30);

    /**
     * @param kakaoCandidates {@code collectCandidatesFromKakao} 출력 ({@code stay/food/cafe/tour})
     * @param userRequested   사용자가 직접 요청한 장소명 — 필수 포함으로 다룬다
     * @return {@code saveAiRouteToDb} 에 그대로 넘길 수 있는 일정 JSON
     */
    public String assemble(TravelPlan plan, PlanInputForm form,
                           Map<String, List<ObjectNode>> kakaoCandidates, Set<String> userRequested) {
        int totalDays = totalDays(plan);
        boolean isDayTrip = totalDays == 1;

        CandidateAdapter adapter = new CandidateAdapter();
        Anchor anchor = toAnchor(adapter, kakaoCandidates);
        List<Candidate> candidates = adapter.toCandidates(kakaoCandidates);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("엔진에 넘길 후보가 없다 — tripId=" + plan.getId());
        }

        RouteConstraints constraints = constraints(plan, form, anchor);
        Set<String> requiredIds = requiredIds(candidates, userRequested);
        DailyCategoryBudget budget = DailyCategoryBudget.of(
                ScheduleDensity.of(form != null ? form.getScheduleDensity() : null));

        // 당일치기에는 숙소가 없지만 앵커는 살려 둔다 — 없으면 밴드 기준점이 출발지(서울)로 떨어져
        // 여행지 전체가 귀가 밴드로 몰린다. 출력에서만 뺀다.
        GeoPoint dayStartPoint = anchor != null ? anchor.location() : constraints.arrivalPoint();
        RouteEngineFactory factory = RouteEngineFactory.withDefaults();
        DayPlanner dayPlanner = factory.dayPlanner();
        SlotBuilder slotBuilder = SlotBuilder.withDefaults();

        List<DailyCandidatePool> pools = BandSplitter.withDefaults()
                .split(anchor, constraints, candidates, totalDays, requiredIds);

        List<DayPlan> dayPlans = new ArrayList<>();
        for (DailyCandidatePool pool : pools) {
            boolean isLastDay = pool.dayIndex() == totalDays - 1;
            GeoPoint returnPoint = isLastDay && !isDayTrip ? constraints.departurePoint() : dayStartPoint;
            List<TimeSlot> slots = slotBuilder.build(pool, anchor, constraints, totalDays, requiredIds);
            dayPlans.add(dayPlanner.plan(slots, dayStartPoint, returnPoint, constraints,
                    pool.dayIndex(), totalDays, budget, requiredIds));
        }

        log.info("[route.engine] tripId={} {}일 · 후보 {}개 · 필수 {}개 · 방문 {}곳",
                plan.getId(), totalDays, candidates.size(), requiredIds.size(),
                dayPlans.stream().mapToInt(DayPlan::totalVisitCount).sum());

        return factory.jsonWriter(adapter)
                .writeJson(dayPlans, isDayTrip ? null : anchor, constraints, plan.getStartDate());
    }

    private static int totalDays(TravelPlan plan) {
        LocalDate start = plan.getStartDate();
        LocalDate end = plan.getEndDate();
        if (start == null || end == null || end.isBefore(start)) {
            return 1;
        }
        return (int) (end.toEpochDay() - start.toEpochDay()) + 1;
    }

    /** 숙소는 기존 로직(카카오)이 고른 것을 그대로 앵커로 쓴다 (결정 2). */
    private static Anchor toAnchor(CandidateAdapter adapter, Map<String, List<ObjectNode>> kakaoCandidates) {
        List<ObjectNode> stays = kakaoCandidates != null
                ? kakaoCandidates.getOrDefault("stay", Collections.emptyList())
                : Collections.emptyList();
        return stays.isEmpty() ? null : adapter.toAnchor(stays.get(0));
    }

    private RouteConstraints constraints(TravelPlan plan, PlanInputForm form, Anchor anchor) {
        GeoPoint homePoint = DeparturePointResolver.resolve(form != null ? form.getDeparture() : null);
        String extraNotes = form != null ? form.getExtraNotes() : null;

        LocalTime arrivalTime = extraTagValue(extraNotes, FIRST_DAY_ARRIVAL_LABEL)
                .orElseGet(() -> defaultArrivalTime(form));
        LocalDateTime firstDayArrival = plan.getStartDate() != null
                ? LocalDateTime.of(plan.getStartDate(), arrivalTime)
                : null;

        // 마지막날 출발 시각은 기본값을 두지 않는다. 17:00 같은 값을 박으면 저녁 창이 접혀
        // 마지막 날 저녁이 통째로 사라지고 식사 횟수·예산 집계가 before 와 달라진다.
        LocalTime lastDayDeparture = extraTagValue(extraNotes, LAST_DAY_DEPARTURE_LABEL).orElse(null);

        return new RouteConstraints(
                firstDayArrival, lastDayDeparture,
                LUNCH_START, LUNCH_END, DINNER_START, DINNER_END,
                null,                 // maxDailyTravelTime — 시간 창이 이미 자른다
                homePoint,            // arrivalPoint : 출발지 대표 좌표 (결정 3)
                homePoint,            // departurePoint : 왕복 가정
                null, null);          // dayStartTime/dayEndTime 기본값 09:00 / 21:00
    }

    /** 결정 4 : 교통수단별 기본 도착 시각. 자차가 이르고 대중교통·기타는 한 시간 늦다. */
    private static LocalTime defaultArrivalTime(PlanInputForm form) {
        String transportType = form != null ? form.getTransportType() : null;
        return transportType != null && transportType.contains("자차") ? CAR_ARRIVAL : TRANSIT_ARRIVAL;
    }

    private static java.util.Optional<LocalTime> extraTagValue(String extraNotes, String label) {
        if (extraNotes == null || extraNotes.isBlank()) {
            return java.util.Optional.empty();
        }
        Matcher matcher = EXTRA_TAG.matcher(extraNotes);
        while (matcher.find()) {
            if (label.equals(matcher.group(1).trim())) {
                return parseTime(matcher.group(2).trim());
            }
        }
        // 태그가 아니라 {label, value} 로 저장된 형태도 훑는다 — 프론트가 그렇게 풀어 넣는다.
        Matcher pair = Pattern
                .compile("\"label\"\\s*:\\s*\"[^\"]*" + Pattern.quote(label) + "[^\"]*\"\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(extraNotes);
        return pair.find() ? parseTime(pair.group(1).trim()) : java.util.Optional.empty();
    }

    private static java.util.Optional<LocalTime> parseTime(String raw) {
        try {
            return java.util.Optional.of(LocalTime.parse(raw.length() == 4 ? "0" + raw : raw));
        } catch (Exception e) {
            log.warn("[route.engine] 시각을 못 읽어 무시한다 — value={}", raw);
            return java.util.Optional.empty();
        }
    }

    /** 사용자 요청 장소명 → 후보 id. 카카오가 못 잡은 이름은 후보에 없어 조용히 빠진다(기존 동작과 같다). */
    private static Set<String> requiredIds(List<Candidate> candidates, Set<String> userRequested) {
        if (userRequested == null || userRequested.isEmpty()) {
            return Set.of();
        }
        return candidates.stream()
                .filter(c -> userRequested.contains(c.name()))
                .map(Candidate::id)
                .collect(Collectors.toSet());
    }
}
