package idusw.sbb.checkin.config;

import idusw.sbb.checkin.domain.expense.entity.Expense;
import idusw.sbb.checkin.domain.expense.repository.ExpenseRepository;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 심사용 시드 (이수환 · 06_이수환_연동작업.md C).
 * 테스트 계정 openapi 에 "오늘 걸친" 부산 2박 3일 여행 1건을 심는다.
 * 심사위원이 접속하면 진행 중인 여행이 지도·목록에 뜨도록.
 *
 * - DataInitializer 와 독립. openapi 계정 유무로만 가드한다.
 * - 매 부팅 시 여행 날짜를 오늘 기준으로 밀어 준다 → 며칠 뒤 심사해도 "진행 중"으로 보인다.
 *
 * "지나온/현재/남은" 하이라이트는 프론트에 판정·렌더 로직이 아직 없다.
 * 이 시드는 여행이 뜨게만 하고, 하이라이트 UI 는 별도 작업으로 남겨 둔다.
 */
@Component
@Order(20)   // DataInitializer 뒤에 실행
@RequiredArgsConstructor
public class JudgeSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JudgeSeedInitializer.class);

    private static final String JUDGE_USERNAME = "openapi";
    private static final String JUDGE_PASSWORD = "2026openapi!";   // 제출 지정 계정
    private static final String SEED_TITLE = "부산 2박 3일 · 진행 중";

    private final UserRepository userRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final ExpenseRepository expenseRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        User judge = userRepository.findByUsername(JUDGE_USERNAME).orElseGet(this::createJudgeUser);

        // 이미 심은 여행이 있으면 날짜만 오늘 기준으로 갱신 (재부팅해도 진행 중 유지)
        // 「목록 맨 앞」이 아니라 이 시드가 심은 여행을 제목으로 집는다.
        // 계정에 다른 여행(3층 시연 시드 등)이 생기면 맨 앞이 그쪽이 되어 엉뚱한 여행을 오늘로 민다.
        TravelPlan existing = travelPlanRepository.findByUserIdOrderByCreatedAtDesc(judge.getId()).stream()
                .filter(p -> SEED_TITLE.equals(p.getTitle()))
                .findFirst().orElse(null);
        if (existing != null) {
            TravelPlan plan = existing;
            plan.setStartDate(LocalDate.now());
            plan.setEndDate(LocalDate.now().plusDays(2));
            plan.setUpdatedAt(LocalDateTime.now());
            travelPlanRepository.save(plan);
            log.info("[심사시드] openapi 여행 날짜를 오늘 기준으로 갱신했습니다.");
            return;
        }

        TravelPlan plan = travelPlanRepository.save(TravelPlan.builder()
                .user(judge)
                .title(SEED_TITLE)
                .destination("부산")
                .startDate(LocalDate.now())          // 오늘 시작
                .endDate(LocalDate.now().plusDays(2)) // 2박 3일
                .isPublic(1)
                .status("FIXED")                      // 프론트는 FIXED 를 확정으로 인식
                .routeJson(ROUTE_JSON)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        seedExpenses(plan, judge);
        log.info("[심사시드] openapi 테스트 계정 + 부산 2박3일 진행 중 여행을 심었습니다.");
    }

    private User createJudgeUser() {
        return userRepository.save(User.builder()
                .username(JUDGE_USERNAME)
                .passwordHash(passwordEncoder.encode(JUDGE_PASSWORD))
                .name("심사용 계정")
                .email("openapi@checkin.local")
                .region("부산")
                .role("USER").status("ACTIVE")
                .lastPwChangedAt(LocalDateTime.now())
                .build());
    }

    // 확정(isEstimated=false) + 추정(true) 을 섞는다 → 예산 신뢰도 계산이 보이게.
    private void seedExpenses(TravelPlan plan, User user) {
        LocalDate d1 = plan.getStartDate();
        save(plan, user, "STAY", "파라다이스 호텔 부산", 530_000L, false, d1);  // 확정: 공개 요금 (근거표와 일치)
        save(plan, user, "FOOD", "자갈치 시장 점심",     36_000L, false, d1);  // 확정: 결제 완료
        save(plan, user, "TOUR", "감천문화마을",          4_000L, false, d1);  // 확정
        save(plan, user, "CAFE", "해운대 카페",          12_000L, true,  d1);  // 추정: 지역 평균
        save(plan, user, "FOOD", "해운대 저녁(회)",      60_000L, true,  d1);  // 추정
        save(plan, user, "STAY", "파라다이스 호텔 부산", 530_000L, true,  d1.plusDays(1)); // 추정: 2박차 (근거표와 일치)
        save(plan, user, "TOUR", "광안리 유람선",        30_000L, true,  d1.plusDays(1)); // 추정
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

    // routeJson — 최상위 배열, 장소 사이 transit, type 4종(stay/food/cafe/tour), 좌표 lat/lng.
    private static final String ROUTE_JSON = """
        [
          {
            "day": 1, "label": "Day 1 · 원도심에서 해운대까지", "budget": "₩642,000",
            "places": [
              {"type":"tour","icon":"🚄","name":"부산역","sub":"관광지 · 도착","stars":"평점 정보 없음","key":"d1p1","time":"09:10","lat":35.1151,"lng":129.0413,"isFound":true},
              {"transit":"🚌 대중교통 · 8.4km · 약 25분 · ₩1,600","pathCoords":[[35.1151,129.0413],[35.0975,129.0107]]},
              {"type":"tour","icon":"🏘️","name":"감천문화마을","sub":"관광지 · 1h · ₩2,000×2","stars":"평점 정보 없음","key":"d1p2","time":"10:40","lat":35.0975,"lng":129.0107,"isFound":true},
              {"transit":"🚌 대중교통 · 3.1km · 약 18분 · ₩1,600","pathCoords":[[35.0975,129.0107],[35.0968,129.0305]]},
              {"type":"food","icon":"🍽️","name":"자갈치 시장","sub":"맛집 · 점심 · ₩18,000×2","stars":"평점 정보 없음","key":"d1p3","time":"12:40","lat":35.0968,"lng":129.0305,"isFound":true},
              {"transit":"🚗 자차 · 11.6km · 약 28분 · ₩4,500","pathCoords":[[35.0968,129.0305],[35.1532,129.1189]]},
              {"type":"tour","icon":"🌊","name":"광안리 해수욕장","sub":"관광지 · 산책","stars":"평점 정보 없음","key":"d1p4","time":"14:20","lat":35.1532,"lng":129.1189,"isFound":true},
              {"transit":"🚗 자차 · 4.7km · 약 15분 · ₩2,000","pathCoords":[[35.1532,129.1189],[35.1587,129.1604]]},
              {"type":"tour","icon":"🏖️","name":"해운대 해수욕장","sub":"관광지 · 저녁 산책","stars":"평점 정보 없음","key":"d1p5","time":"17:40","lat":35.1587,"lng":129.1604,"isFound":true},
              {"transit":"🚶 도보 · 0.4km · 약 6분 · ₩0","pathCoords":[[35.1587,129.1604],[35.1601,129.1601]]},
              {"type":"stay","icon":"🏨","name":"파라다이스 호텔 부산","sub":"숙소 · ₩530,000","stars":"평점 정보 없음","key":"d1p6","time":"20:30","lat":35.1601,"lng":129.1601,"isFound":true}
            ]
          },
          {
            "day": 2, "label": "Day 2 · 원거리 권역", "budget": "₩90,000",
            "places": [
              {"type":"tour","icon":"🌉","name":"오륙도 스카이워크","sub":"관광지 · 1h","stars":"평점 정보 없음","key":"d2p1","time":"10:00","lat":35.0975,"lng":129.1213,"isFound":true},
              {"transit":"🚗 자차 · 9.0km · 약 22분 · ₩3,000","pathCoords":[[35.0975,129.1213],[35.2281,129.0857]]},
              {"type":"food","icon":"🍜","name":"부산 밀면 맛집","sub":"맛집 · 점심 · ₩9,000×2","stars":"평점 정보 없음","key":"d2p2","time":"12:30","lat":35.2281,"lng":129.0857,"isFound":true}
            ]
          },
          {
            "day": 3, "label": "Day 3 · 귀가 방향", "budget": "₩14,000",
            "places": [
              {"type":"cafe","icon":"☕","name":"전포 카페거리","sub":"카페 · ₩7,000×2","stars":"평점 정보 없음","key":"d3p1","time":"10:30","lat":35.1568,"lng":129.0648,"isFound":true},
              {"transit":"🚶 도보 · 1.2km · 약 15분 · ₩0","pathCoords":[[35.1568,129.0648],[35.1151,129.0413]]},
              {"type":"tour","icon":"🚄","name":"부산역","sub":"관광지 · 귀가","stars":"평점 정보 없음","key":"d3p2","time":"12:00","lat":35.1151,"lng":129.0413,"isFound":true}
            ]
          }
        ]
        """;
}
