//    여행 플랜 DB 조회 기능
//    TravelPlan 엔티티에 대한 JPA Repository 인터페이스.
//    내 플랜 목록(최신순), 공개 플랜 목록 조회 쿼리를 제공한다.

package idusw.sbb.triplinker.domain.plan.repository;


import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {

    /** 내 여행 일정 목록 (최신순) */
    List<TravelPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 공개 플랜 목록 (커뮤니티 스크랩 대상) */
    List<TravelPlan> findByIsPublicOrderByCreatedAtDesc(int isPublic);
}