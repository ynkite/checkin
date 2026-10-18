package idusw.sbb.checkin.domain.plan;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.budget.BudgetFitService;
import idusw.sbb.checkin.domain.expense.controller.ExpenseController;
import idusw.sbb.checkin.domain.expense.repository.ExpenseRepository;
import idusw.sbb.checkin.domain.expense.service.ExpenseService;
import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.PlanInputFormRepository;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.plan.service.TravelPlanServiceImpl;
import idusw.sbb.checkin.domain.plan.support.PlanAccess;
import idusw.sbb.checkin.domain.planshare.repository.TripMemberRepository;
import idusw.sbb.checkin.domain.post.repository.PostRepository;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 10-19 전체 적대 점검에서 나온 것 — 공유 링크 예산 탭 · 챗봇 값 저장 500 · 수정 뒤 저장 유실 · 내 여행 순서 */
class QaFullFixTest {

    TravelPlanRepository plans = mock(TravelPlanRepository.class);
    PlanInputFormRepository forms = mock(PlanInputFormRepository.class);
    TripMemberRepository members = mock(TripMemberRepository.class);
    PostRepository posts = mock(PostRepository.class);
    PlanAccess access = new PlanAccess(plans, members, posts);
    TravelPlanServiceImpl service = new TravelPlanServiceImpl(
            plans, forms, mock(UserRepository.class), mock(ExpenseRepository.class), members, access);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 공유_링크는_로그인_없이_예산_탭을_연다() {
        TravelPlan plan = plan();
        BudgetFitService fit = mock(BudgetFitService.class);
        ExpenseController controller = new ExpenseController(mock(ExpenseService.class), fit, access);

        shareHeader("read-tok");
        assertThat(controller.budgetFit(10L).getStatusCode().value()).isEqualTo(200);
        assertThat(plan.getIsPublic()).isZero();   /* 비공개여도 토큰이 있으면 본다 */
    }

    @Test
    void 챗봇이_바꾼_값은_여행이_가리키는_폼에_쓴다() {
        /* 2단계를 두 번 저장해 폼 행이 둘인 여행. plan_id 로 하나를 고르면 터지던 자리 */
        TravelPlan plan = plan();
        PlanInputForm current = PlanInputForm.builder().plan(plan).budget(100_000L).build();
        plan.linkInputForm(current);
        login(1L);

        service.updateInputForm(1L, 10L, Map.of("budget", "40만원"));

        assertThat(current.getBudget()).isEqualTo(40L);
        assertThat(current.getPreferenceSource()).isEqualTo("CHATBOT");
    }

    @Test
    void 수정으로_바꾸면_묵은_draft_가_본문이_되고_비워진다() {
        TravelPlan plan = plan();
        plan.setStatus("FIXED");
        plan.setRouteJson("[old]");
        plan.setDraftRouteJson("[edited-while-fixed]");
        login(1L);

        service.updatePlanStatus(1L, 10L, "DRAFT");
        assertThat(plan.getStatus()).isEqualTo("DRAFT");
        assertThat(plan.getRouteJson()).isEqualTo("[edited-while-fixed]");
        assertThat(plan.getDraftRouteJson()).isNull();

        /* 수정 중 저장은 routeJson 에 간다. 다음 확정이 그것을 덮지 않아야 한다 */
        plan.setRouteJson("[reordered]");
        service.updatePlanStatus(1L, 10L, "FIXED");
        assertThat(plan.getStatus()).isEqualTo("FIXED");
        assertThat(plan.getRouteJson()).isEqualTo("[reordered]");
    }

    @Test
    void 내_여행은_진행중_다가오는_지난_순이다() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        TravelPlan past1 = dated("past1", "2026-09-07", "2026-09-09");
        TravelPlan past2 = dated("past2", "2026-09-28", "2026-09-30");
        TravelPlan now = dated("now", "2026-10-17", "2026-10-19");
        TravelPlan soon = dated("soon", "2026-10-24", "2026-10-26");
        TravelPlan later = dated("later", "2026-11-05", "2026-11-06");

        List<String> order = java.util.stream.Stream.of(past1, soon, later, now, past2)
                .sorted(TravelPlanServiceImpl.listOrder(today)).map(TravelPlan::getTitle).toList();

        assertThat(order).containsExactly("now", "soon", "later", "past2", "past1");
    }

    private TravelPlan plan() {
        User owner = User.builder().username("owner").build();
        ReflectionTestUtils.setField(owner, "id", 1L);
        TravelPlan p = TravelPlan.builder().user(owner).shareReadToken("read-tok").build();
        ReflectionTestUtils.setField(p, "id", 10L);
        when(plans.findById(10L)).thenReturn(Optional.of(p));
        return p;
    }

    private static TravelPlan dated(String title, String start, String end) {
        return TravelPlan.builder().title(title).startDate(LocalDate.parse(start)).endDate(LocalDate.parse(end)).build();
    }

    private static void login(Long userId) {
        CustomUserDetails u = new CustomUserDetails(userId, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private static void shareHeader(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(PlanAccess.SHARE_HEADER, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }
}
