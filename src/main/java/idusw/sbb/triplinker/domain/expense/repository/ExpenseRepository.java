/**
 * 가계부 Repository
 * - EXPENSES 테이블에 접근하는 DB 조회 인터페이스이다.
 * - 사용자별 전체 지출 내역과 카테고리별 지출 내역을 조회한다.
 */
package idusw.sbb.triplinker.domain.expense.repository;

import idusw.sbb.triplinker.domain.expense.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByPlanId(Long planId);

    //AI 일정 재생성 시 기존 예상 비용 초기화
    @Modifying
    @Query("DELETE FROM Expense e WHERE e.plan.id = :planId AND e.isEstimated = true")
    void deleteEstimatedByPlanId(@Param("planId") Long planId);

    @Query("SELECT e FROM Expense e WHERE e.plan IN (SELECT t FROM TravelPlan t WHERE t.user.id = :userId)")
    List<Expense> findByUserId(@Param("userId") Long userId);

    @Query("SELECT e FROM Expense e WHERE e.category = :category AND e.plan IN (SELECT t FROM TravelPlan t WHERE t.user.id = :userId)")
    List<Expense> findByUserIdAndCategory(@Param("userId") Long userId, @Param("category") String category);
}