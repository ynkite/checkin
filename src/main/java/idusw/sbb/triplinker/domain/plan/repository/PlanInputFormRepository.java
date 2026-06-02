//    여행 취향 폼 DB 조회 기능
//    PlanInputForm 엔티티에 대한 JPA Repository 인터페이스.
//    특정 플랜의 취향 폼 단건 조회, 이전 취향 불러오기(AUTO_LOADED 제외한 가장 최근 폼) 쿼리를 제공한다.

package idusw.sbb.triplinker.domain.plan.repository;


import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanInputFormRepository extends JpaRepository<PlanInputForm, Long> {

    /** planId로 취향 폼 조회 (1:1) */
    Optional<PlanInputForm> findByPlanId(Long planId);

    /**
     * 이전 취향 불러오기 — 해당 유저의 가장 최근 폼 조회
     * preferenceSource 가 UI_CLICK 또는 CHATBOT 인 것만 (AUTO_LOADED 제외)
     */
    Optional<PlanInputForm> findTopByUserIdAndPreferenceSourceNotOrderByCreatedAtDesc(
            Long userId, String excludeSource);
}