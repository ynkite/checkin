package idusw.sbb.triplinker.domain.plan.repository;

import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanInputFormRepository extends JpaRepository<PlanInputForm, Long> {

    Optional<PlanInputForm> findByPlanId(Long planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM PlanInputForm f WHERE f.plan.id = :planId")
    Optional<PlanInputForm> findByPlanIdWithLock(@Param("planId") Long planId);

    List<PlanInputForm> findByUserIdAndPreferenceSourceNotOrderByCreatedAtDesc(
            Long userId, String preferenceSource);
}