/**
 * 여행 계획 Repository
 * - TRAVEL_PLANS 테이블에서 사용자의 여행 기록을 조회한다.
 * - 종료일 기준으로 다가오는 여행과 지난 여행을 구분한다.
 */
package idusw.sbb.triplinker.domain.plan.repository;

import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {

    @Query("SELECT t FROM TravelPlan t WHERE t.user.id = :userId AND t.endDate >= :now ORDER BY t.startDate ASC")
    Page<TravelPlan> findUpcomingTrips(@Param("userId") Long userId, @Param("now") LocalDate now, Pageable pageable);

    @Query("SELECT t FROM TravelPlan t WHERE t.user.id = :userId AND t.endDate < :now ORDER BY t.startDate DESC")
    Page<TravelPlan> findPastTrips(@Param("userId") Long userId, @Param("now") LocalDate now, Pageable pageable);
}