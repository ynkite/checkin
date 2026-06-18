package idusw.sbb.triplinker.domain.plan.repository;

import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {

    @Query("SELECT t FROM TravelPlan t WHERE t.user.id = :userId AND t.endDate >= :now ORDER BY t.startDate ASC")
    Page<TravelPlan> findUpcomingTrips(@Param("userId") Long userId, @Param("now") LocalDate now, Pageable pageable);

    @Query("SELECT t FROM TravelPlan t WHERE t.user.id = :userId AND t.endDate < :now ORDER BY t.startDate DESC")
    Page<TravelPlan> findPastTrips(@Param("userId") Long userId, @Param("now") LocalDate now, Pageable pageable);

    List<TravelPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<TravelPlan> findByIsPublicOrderByCreatedAtDesc(int isPublic);

    @Modifying
    @Query("UPDATE TravelPlan t SET t.destination = :destination WHERE t.id = :tripId")
    void updateDestination(@Param("tripId") Long tripId, @Param("destination") String destination);

    // 관리자 통계 - 기간별 신규 일정 생성 수 집계
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 관리자 통계 - 일자별 신규 일정 생성 수 (추이 차트용)
    @Query("SELECT FUNCTION('DATE', t.createdAt), COUNT(t) FROM TravelPlan t " +
            "WHERE t.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('DATE', t.createdAt)")
    List<Object[]> countDailyNewTrips(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 관리자 통계 - 기간 내 인기 여행지 Top N
    @Query("SELECT t.destination, COUNT(t) FROM TravelPlan t " +
            "WHERE t.createdAt BETWEEN :start AND :end GROUP BY t.destination ORDER BY COUNT(t) DESC")
    List<Object[]> findTopDestinations(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);
}