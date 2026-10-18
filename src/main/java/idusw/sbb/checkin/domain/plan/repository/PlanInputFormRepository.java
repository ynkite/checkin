package idusw.sbb.checkin.domain.plan.repository;

import idusw.sbb.checkin.domain.plan.entity.PlanInputForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanInputFormRepository extends JpaRepository<PlanInputForm, Long> {

    /* 2단계를 다시 저장하면 같은 plan_id 로 행이 하나 더 생긴다. 최신 것 하나만 고른다 */
    Optional<PlanInputForm> findFirstByPlanIdOrderByIdDesc(Long planId);

    List<PlanInputForm> findByUserIdAndPreferenceSourceNotOrderByCreatedAtDesc(
            Long userId, String preferenceSource);
}