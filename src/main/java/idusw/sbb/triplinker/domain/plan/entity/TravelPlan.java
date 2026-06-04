/**
 * 여행 계획 엔티티
 * - 사용자가 생성한 여행 일정을 TRAVEL_PLANS 테이블에 저장한다.
 * - 여행 제목, 시작일, 종료일, 작성일 정보를 관리한다.
 */
package idusw.sbb.triplinker.domain.plan.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRAVEL_PLANS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}