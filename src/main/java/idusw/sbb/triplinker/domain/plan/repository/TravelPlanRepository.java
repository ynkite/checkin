package idusw.sbb.triplinker.domain.plan.repository;

import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {

    @Query("SELECT t FROM TravelPlan t WHERE t.user.id = :userId AND t.endDate >= :now ORDER BY t.startDate ASC")
    Page<TravelPlan> findUpcomingTrips(@Param("userId") Long userId, @Param("now") LocalDate now, Pageable pageable);

    @Query("SELECT t FROM TravelPlan t WHERE t.user.id = :userId AND t.endDate < :now ORDER BY t.startDate DESC")
    Page<TravelPlan> findPastTrips(@Param("userId") Long userId, @Param("now") LocalDate now, Pageable pageable);

    //내 여행 일정 목록 (최신순)
    List<TravelPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 공개 플랜 목록 (커뮤니티 스크랩 대상)
    List<TravelPlan> findByIsPublicOrderByCreatedAtDesc(int isPublic);

    @Modifying
    @Query("UPDATE TravelPlan t SET t.destination = :destination WHERE t.id = :tripId")
    void updateDestination(@Param("tripId") Long tripId, @Param("destination") String destination);
}