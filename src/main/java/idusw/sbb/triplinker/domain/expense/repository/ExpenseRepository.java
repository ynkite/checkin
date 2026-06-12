/**
 * 가계부 Repository
 * - EXPENSES 테이블에 접근하는 DB 조회 인터페이스이다.
 * - 사용자별 전체 지출 내역과 카테고리별 지출 내역을 조회한다.
 */
package idusw.sbb.triplinker.domain.expense.repository;

import idusw.sbb.triplinker.domain.expense.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByUserIdAndCategory(Long userId, String category);
    List<Expense> findByUserId(Long userId);    // 가계부 전체조회 메서드
}