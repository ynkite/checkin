package idusw.sbb.triplinker.domain.planshare.repository;
import idusw.sbb.triplinker.domain.planshare.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;
import idusw.sbb.triplinker.domain.planshare.entity.PlanRole;
import java.util.List;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {
    List<TripMember> findByTravelPlanId(Long travelPlanId);
    boolean existsByTravelPlanIdAndUserEmail(Long travelPlanId, String email);

    boolean existsByTravelPlanIdAndUserIdAndRole(Long travelPlanId, Long userId, PlanRole role);
}