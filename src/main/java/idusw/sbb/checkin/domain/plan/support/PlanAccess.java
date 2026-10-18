package idusw.sbb.checkin.domain.plan.support;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.planshare.entity.PlanRole;
import idusw.sbb.checkin.domain.planshare.repository.TripMemberRepository;
import idusw.sbb.checkin.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 여행 하나를 누가 보고 고칠 수 있는가 — 여기 한곳에서 정한다.
 *
 * <p>보기: 소유자 · 초대된 멤버 · 공개 여행 · 공개 후기에 붙은 여행 · 공유 링크 토큰을 가진 사람.
 * <p>고치기: 소유자 · 편집자로 초대된 멤버. 공유 링크 토큰만으로는 고칠 수 없다.
 * <p>공유 링크 토큰은 {@code X-Share-Token} 머리말로 받는다. /trip/{토큰} 화면이 붙여 보낸다.
 */
@Component
@RequiredArgsConstructor
public class PlanAccess {

    public static final String SHARE_HEADER = "X-Share-Token";

    private final TravelPlanRepository plans;
    private final TripMemberRepository members;
    private final PostRepository posts;

    public TravelPlan read(Long tripId) {
        TravelPlan p = find(tripId);
        if (!canRead(p, currentUserId(), shareToken())) throw new AccessDeniedException("이 여행을 볼 권한이 없습니다.");
        return p;
    }

    public TravelPlan edit(Long tripId) {
        TravelPlan p = find(tripId);
        if (!canEdit(p, currentUserId())) throw new AccessDeniedException("이 여행을 고칠 권한이 없습니다.");
        return p;
    }

    public TravelPlan own(Long tripId) {
        TravelPlan p = find(tripId);
        if (!isOwner(p, currentUserId())) throw new AccessDeniedException("여행을 만든 사람만 할 수 있습니다.");
        return p;
    }

    public boolean canEdit(TravelPlan p, Long userId) {
        return isOwner(p, userId)
                || (userId != null && members.existsByTravelPlanIdAndUserIdAndRole(p.getId(), userId, PlanRole.EDITOR));
    }

    public boolean canRead(TravelPlan p, Long userId, String token) {
        if (p.getIsPublic() == 1 || canEdit(p, userId)) return true;
        if (token != null && (token.equals(p.getShareReadToken()) || token.equals(p.getShareEditToken()))) return true;
        if (userId != null && members.existsByTravelPlanIdAndUserId(p.getId(), userId)) return true;
        return posts.existsVisibleByPlanId(p.getId());
    }

    private static boolean isOwner(TravelPlan p, Long userId) {
        return userId != null && p.getUser() != null && userId.equals(p.getUser().getId());
    }

    private TravelPlan find(Long tripId) {
        return plans.findById(tripId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));
    }

    public static Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof CustomUserDetails u) ? u.getUserId() : null;
    }

    private static String shareToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes r)) return null;
        String t = r.getRequest().getHeader(SHARE_HEADER);
        return (t == null || t.isBlank()) ? null : t;
    }
}
