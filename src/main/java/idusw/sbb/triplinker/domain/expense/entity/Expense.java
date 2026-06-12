/**
 * 가계부 지출 엔티티
 * - 사용자가 입력한 지출 내역을 EXPENSES 테이블에 저장한다.
 * - 금액, 카테고리, 지출일, 메모 정보를 관리한다.
 */
package idusw.sbb.triplinker.domain.expense.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "EXPENSES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Long amount;  // DB BIGINT 타입과 매핑되는 지출 금액

    @Column(nullable = false, length = 20)
    private String category; // STAY, FOOD, TOUR, CAFE

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(length = 255)
    private String memo;
}