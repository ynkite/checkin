package idusw.sbb.checkin.config;

import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.expense.repository.ExpenseRepository;
import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.PlanInputFormRepository;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.route.RouteJson;
import org.springframework.core.io.ClassPathResource;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 검증 루프 시연 시드 — 홍은표 (예산 엔진 3층 · 07_예산엔진.md).
 *
 * 「예측 정확도」 카드는 <b>다녀온 여행</b>(endDate 가 지난 여행)에 예측·실측이 둘 다 있어야 뜬다.
 * 심사 계정에는 진행 중인 부산 여행 1건뿐이라 카드가 안 떠서, 지난 여행 3건을 따로 심는다.
 *
 * - 여행 자체는 별도 파일로 심는다. JudgeSeedInitializer 의 시드 데이터는 건드리지 않는다
 * - 매 부팅 때 날짜를 오늘 기준으로 다시 맞춘다. 지난 여행이어야 카드에 뜨기 때문에
 *   날짜가 한 번이라도 밀리면 시연이 통째로 사라진다
 * - 제목으로 찾는다. 재부팅해도 여행·지출을 다시 심지 않는다
 *
 * 화면·응답 문구에 「시연」 표기가 들어간다. 실사용 축적치가 아니다.
 */
@Component
@Order(21)   // JudgeSeedInitializer(20) 뒤
@RequiredArgsConstructor
public class BudgetLoopSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BudgetLoopSeedInitializer.class);

    private static final String JUDGE_USERNAME = "openapi";
    private static final String TITLE_MARK = "시연";   // 가드 겸 화면 표기

    private final UserRepository userRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final ExpenseRepository expenseRepository;
    private final PlanInputFormRepository planInputFormRepository;

    @Override
    public void run(String... args) {
        User judge = userRepository.findByUsername(JUDGE_USERNAME).orElse(null);
        if (judge == null) return;   // 심사 계정이 없으면 심을 데가 없다

        List<TravelPlan> mine = travelPlanRepository.findByUserIdOrderByCreatedAtDesc(judge.getId()).stream()
                .filter(p -> p.getTitle() != null && p.getTitle().contains(TITLE_MARK))
                .toList();

        // 예측 / 실측 — 오차가 한쪽으로만 쏠리지 않게 초과 2건 · 미달 1건을 섞는다
        seedTrip(judge, mine, "경주 1박 2일 · 시연", "경주", 63, 1, 120_000, 120_000, 48_000, 55_000, 30_000, 30_000);
        seedTrip(judge, mine, "강릉 2박 3일 · 시연", "강릉", 42, 2, 240_000, 220_000, 70_000, 66_000, 30_000, 20_000);
        seedTrip(judge, mine, "전주 1박 2일 · 시연", "전주", 21, 1,  90_000,  90_000, 42_000, 51_000, 20_000, 20_000);

        /* 경로와 취향 입력. 전에는 여행만 심어서 지도·내 여행에서 누르면 빈 화면이었다.
           장소·좌표는 카카오 로컬 검색, 구간은 카카오모빌리티 길찾기 값이다(resources/seed/demo-routes).
           숙박·식비 합은 위 예측값과 같다. 이미 경로·취향이 있으면 건드리지 않는다 */
        List<TravelPlan> seeded = travelPlanRepository.findByUserIdOrderByCreatedAtDesc(judge.getId()).stream()
                .filter(p -> p.getTitle() != null && p.getTitle().contains(TITLE_MARK))
                .toList();
        fillRoute(judge, seeded, "경주 1박 2일 · 시연", "gyeongju",  "[\"문화·역사\",\"음식 탐방\"]", 198_000L);
        fillRoute(judge, seeded, "강릉 2박 3일 · 시연", "gangneung", "[\"힐링\",\"음식 탐방\"]",     340_000L);
        fillRoute(judge, seeded, "전주 1박 2일 · 시연", "jeonju",    "[\"문화·역사\",\"음식 탐방\"]", 152_000L);
    }

    private void fillRoute(User user, List<TravelPlan> seeded, String title, String file,
                           String styles, long budget) {
        TravelPlan plan = seeded.stream().filter(p -> title.equals(p.getTitle())).findFirst().orElse(null);
        if (plan == null) return;
        boolean changed = false;

        if (!RouteJson.usable(plan.getRouteJson())) {
            String json = readRoute(file);
            if (RouteJson.usable(json)) {
                plan.setRouteJson(json);
                changed = true;
            } else {
                log.warn("[3층시드] {} 경로 파일을 읽지 못해 비워 둡니다.", title);
            }
        }

        /* 취향이 없으면 「경로 다시 만들기」가 실패한다. 경로 파일과 맞는 값만 넣는다 —
           구간이 자차라 자차, 숙소가 호텔이라 호텔. 출발지는 근거가 없어 비워 둔다 */
        if (plan.getForm() == null) {
            PlanInputForm form = planInputFormRepository.save(PlanInputForm.builder()
                    .plan(plan).user(user)
                    .transportType("🚗 자차")
                    .accommodationType("호텔")
                    .accommodationOptions("[]")
                    .companionType("커플")
                    .companionCount(2)
                    .travelStyles(styles)
                    .dietaryInfo("[]")
                    .scheduleDensity("여유롭게")
                    .budget(budget)                   // 숙박·식비·관광교통 예측의 합
                    .preferenceSource("AUTO_LOADED")
                    .build());
            plan.linkInputForm(form);
            changed = true;
        }

        if (changed) {
            travelPlanRepository.save(plan);
            log.info("[3층시드] {} 에 경로·취향을 채웠습니다.", title);
        }
    }

    private String readRoute(String file) {
        try (var in = new ClassPathResource("seed/demo-routes/" + file + ".json").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 이미 있으면 날짜만 다시 맞추고, 없으면 여행 + 예측·실측 지출을 심는다. */
    private void seedTrip(User user, List<TravelPlan> mine, String title, String destination,
                          int daysAgo, int nights,
                          long stayEst, long stayAct, long foodEst, long foodAct, long etcEst, long etcAct) {
        LocalDate start = LocalDate.now().minusDays(daysAgo);
        LocalDate end   = start.plusDays(nights);

        TravelPlan found = mine.stream().filter(p -> title.equals(p.getTitle())).findFirst().orElse(null);
        if (found != null) {
            // 날짜가 오늘 쪽으로 밀렸으면 되돌린다 — 지난 여행이 아니면 검증 루프 표본에서 빠진다
            if (!end.equals(found.getEndDate())) {
                found.setStartDate(start);
                found.setEndDate(end);
                travelPlanRepository.save(found);
                log.info("[3층시드] {} 날짜를 지난 여행으로 되돌렸습니다.", title);
            }
            return;
        }

        TravelPlan plan = travelPlanRepository.save(TravelPlan.builder()
                .user(user)
                .title(title)
                .destination(destination)
                .startDate(start)
                .endDate(end)                 // 과거 → 「다녀온 여행」
                .isPublic(0)
                .status("FIXED")              // 목록 필터가 FIXED 만 통과시킨다
                .build());

        pair(plan, user, "STAY", "숙박비",    stayEst, stayAct, start);
        pair(plan, user, "FOOD", "식비",      foodEst, foodAct, start);
        pair(plan, user, "TOUR", "관광·교통", etcEst,  etcAct,  start);
        log.info("[3층시드] {} 을(를) 심었습니다.", title);
    }

    private void pair(TravelPlan plan, User user, String category, String desc,
                      long estimated, long actual, LocalDate date) {
        save(plan, user, category, desc + " (예측)", estimated, true,  date);
        save(plan, user, category, desc,             actual,    false, date);
    }

    private void save(TravelPlan plan, User user, String category, String desc,
                      long amount, boolean estimated, LocalDate date) {
        expenseRepository.save(Expense.builder()
                .plan(plan).user(user)
                .category(category).description(desc)
                .amount(amount).isEstimated(estimated)
                .expenseDate(date)
                .build());
    }
}
