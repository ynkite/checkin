package idusw.sbb.checkin.domain.plan;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.expense.repository.ExpenseRepository;
import idusw.sbb.checkin.domain.expense.service.ExpenseService;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.plan.support.PlanAccess;
import idusw.sbb.checkin.domain.planshare.dto.ShareInviteRequestDto;
import idusw.sbb.checkin.domain.planshare.entity.PlanRole;
import idusw.sbb.checkin.domain.planshare.repository.TripMemberRepository;
import idusw.sbb.checkin.domain.planshare.service.TripShareService;
import idusw.sbb.checkin.domain.post.repository.PostRepository;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 남의 여행을 보거나 고치지 못하게 막는 자리. 소유자 1번, 남 2번, 여행 10번 */
class PlanAccessTest {

    TravelPlanRepository plans = mock(TravelPlanRepository.class);
    TripMemberRepository members = mock(TripMemberRepository.class);
    PostRepository posts = mock(PostRepository.class);
    PlanAccess access = new PlanAccess(plans, members, posts);
    TravelPlan plan = plan(0);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 남의_비공개_여행은_비로그인도_다른_회원도_못_본다() {
        assertThatThrownBy(() -> access.read(10L)).isInstanceOf(AccessDeniedException.class);
        login(2L);
        assertThatThrownBy(() -> access.read(10L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 소유자_공개여행_멤버_공유토큰_공개후기는_볼_수_있다() {
        login(1L);
        assertThat(access.read(10L)).isSameAs(plan);

        login(2L);
        when(members.existsByTravelPlanIdAndUserId(10L, 2L)).thenReturn(true);
        assertThat(access.read(10L)).isSameAs(plan);
        when(members.existsByTravelPlanIdAndUserId(10L, 2L)).thenReturn(false);

        SecurityContextHolder.clearContext();
        shareHeader("read-tok");
        assertThat(access.read(10L)).isSameAs(plan);
        shareHeader("틀린토큰");
        assertThatThrownBy(() -> access.read(10L)).isInstanceOf(AccessDeniedException.class);

        when(posts.existsVisibleByPlanId(10L)).thenReturn(true);
        assertThat(access.read(10L)).isSameAs(plan);

        plan.setIsPublic(1);
        when(posts.existsVisibleByPlanId(10L)).thenReturn(false);
        assertThat(access.read(10L)).isSameAs(plan);
    }

    @Test
    void 고치기는_소유자와_편집자만_공유토큰과_열람자는_못_고친다() {
        plan.setIsPublic(1);
        shareHeader("edit-tok");
        login(2L);
        assertThatThrownBy(() -> access.edit(10L)).isInstanceOf(AccessDeniedException.class);

        when(members.existsByTravelPlanIdAndUserIdAndRole(10L, 2L, PlanRole.EDITOR)).thenReturn(true);
        assertThat(access.edit(10L)).isSameAs(plan);
        assertThatThrownBy(() -> access.own(10L)).isInstanceOf(AccessDeniedException.class);

        login(1L);
        assertThat(access.own(10L)).isSameAs(plan);
    }

    @Test
    void 남의_여행에_자기를_편집자로_초대하지_못한다() {
        UserRepository users = mock(UserRepository.class);
        TripShareService share = new TripShareService(members, plans, users, access, null);
        login(2L);
        ShareInviteRequestDto dto = new ShareInviteRequestDto();
        dto.setEmail("me@x.com");
        dto.setRole("EDITOR");

        assertThatThrownBy(() -> share.inviteMember(10L, dto)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> share.generateShareLink(10L, PlanRole.EDITOR)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> share.getMembers(10L)).isInstanceOf(AccessDeniedException.class);
        verify(members, never()).save(any());
    }

    @Test
    void 다른_여행의_지출_번호로는_고치거나_지우지_못한다() {
        ExpenseRepository expenses = mock(ExpenseRepository.class);
        TravelPlan other = plan(0);
        ReflectionTestUtils.setField(other, "id", 99L);
        var e = idusw.sbb.checkin.domain.expense.entity.Expense.builder().plan(other).amount(1000L).build();
        when(expenses.findById(5L)).thenReturn(Optional.of(e));
        ExpenseService svc = new ExpenseService(expenses, plans, null);

        assertThatThrownBy(() -> svc.deleteExpense(10L, 5L)).isInstanceOf(EntityNotFoundException.class);
        verify(expenses, never()).delete(any());
        svc.deleteExpense(99L, 5L);
        verify(expenses).delete(e);
    }

    private TravelPlan plan(int isPublic) {
        User owner = User.builder().username("owner").build();
        ReflectionTestUtils.setField(owner, "id", 1L);
        TravelPlan p = TravelPlan.builder().user(owner).isPublic(isPublic)
                .shareReadToken("read-tok").shareEditToken("edit-tok").build();
        ReflectionTestUtils.setField(p, "id", 10L);
        when(plans.findById(10L)).thenReturn(Optional.of(p));
        return p;
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
