package idusw.sbb.triplinker.config;

import idusw.sbb.triplinker.domain.expense.entity.Expense;
import idusw.sbb.triplinker.domain.expense.repository.ExpenseRepository;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.entity.PostLike;
import idusw.sbb.triplinker.domain.post.repository.PostLikeRepository;
import idusw.sbb.triplinker.domain.post.repository.PostRepository;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DummyDataInit implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final ExpenseRepository expenseRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
    }

    // 유저 + 게시글 더미(임시)
    private void createUserAndPostDummies() {
        if (userRepository.findByUsername("posttest").isPresent()) return;

        User dummyUser = userRepository.save(User.builder()
                .username("posttest")
                .passwordHash(passwordEncoder.encode("Asdf1234!"))
                .name("김테스트")
                .email("test@test")
                .region("서울")
                .role("USER")
                .status("ACTIVE")
                .lastPwChangedAt(LocalDateTime.now())
                .build());

        Post post1 = Post.builder()
                .user(dummyUser)
                .title("제주도 3박 4일 가성비 힐링 코스")
                .content("정말 재밌는 여행이었습니다. 강력 추천!")
                .styleTags("[\"가성비\", \"힐링\"]")
                .isPublic(true)
                .build();
        post1.increaseViewCount();
        post1.increaseLikeCount();

        Post post2 = Post.builder()
                .user(dummyUser)
                .title("부산 바다뷰 카페 투어 후기")
                .content("바다 뷰가 끝내주네요.")
                .styleTags("[\"바다뷰\", \"카페\"]")
                .isPublic(true)
                .build();

        postRepository.save(post1);
        postRepository.save(post2);

        postLikeRepository.save(PostLike.builder()
                .user(dummyUser)
                .post(post1)
                .build());

        System.out.println("===== [더미] 유저 + 게시글 생성 완료 =====");
    }

    //가계부 더미(임시)
    private void createExpenseDummies() {
        //전용 테스트 유저 조회 또는 생성
        User user = userRepository.findByUsername("ledgertest")
                .orElseGet(() -> userRepository.save(User.builder()
                        .username("ledgertest")
                        .passwordHash(passwordEncoder.encode("Asdf1234!"))
                        .name("가계부테스트")
                        .email("ledger@test")
                        .region("서울")
                        .role("USER")
                        .status("ACTIVE")
                        .lastPwChangedAt(LocalDateTime.now())
                        .build()));

        //이미 플랜이 있으면 중복 생성 방지
        if (!travelPlanRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).isEmpty()) return;

        TravelPlan plan = travelPlanRepository.save(TravelPlan.builder()
                .user(user)
                .title("제주도 3박4일 힐링 여행")
                .destination("제주도")
                .startDate(LocalDate.of(2025, 8, 1))
                .endDate(LocalDate.of(2025, 8, 4))
                .status("CONFIRMED")
                .build());

        //AI 예상 비용 (isEstimated = true)
        expenseRepository.save(Expense.builder().plan(plan).category("STAY").description("제주 숙소 3박").amount(180_000L).isEstimated(true).expenseDate(LocalDate.of(2025, 8, 1)).build());
        expenseRepository.save(Expense.builder().plan(plan).category("FOOD").description("식비 예상").amount(96_000L).isEstimated(true).expenseDate(LocalDate.of(2025, 8, 1)).build());
        expenseRepository.save(Expense.builder().plan(plan).category("TOUR").description("관광지 입장료").amount(48_000L).isEstimated(true).expenseDate(LocalDate.of(2025, 8, 2)).build());
        expenseRepository.save(Expense.builder().plan(plan).category("CAFE").description("카페/음료 예상").amount(30_000L).isEstimated(true).expenseDate(LocalDate.of(2025, 8, 3)).build());

        //실제 지출 (isEstimated = false)
        expenseRepository.save(Expense.builder().plan(plan).category("FOOD").description("흑돼지 맛집").amount(55_000L).isEstimated(false).expenseDate(LocalDate.of(2025, 8, 2)).build());
        expenseRepository.save(Expense.builder().plan(plan).category("CAFE").description("협재 오션뷰 카페").amount(18_000L).isEstimated(false).expenseDate(LocalDate.of(2025, 8, 3)).build());

        System.out.println("===== [더미] 가계부 (TravelPlan + Expense) 생성 완료 =====");
    }
}