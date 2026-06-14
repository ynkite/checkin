//    여행 플랜 서비스 인터페이스
//    플랜 생성, 취향 폼 저장, 상세/목록 조회, 이전 플랜 불러오기 기능의 메서드를 정의한다.

package idusw.sbb.triplinker.domain.plan.service;



import idusw.sbb.triplinker.domain.plan.dto.PlanCreateDto;
import idusw.sbb.triplinker.domain.plan.dto.PlanDetailResponseDto;
import idusw.sbb.triplinker.domain.plan.dto.PlanInputFormSaveDto;

import java.util.List;
import java.util.Map;

public interface TravelPlanService {

    /**
     * ① TRAVEL_PLANS 생성 (form_id = NULL)
     * @return 생성된 tripId
     */
    Long createPlan(Long userId, PlanCreateDto dto);

    /**
     * ② PLAN_INPUT_FORM 저장
     * ③ TRAVEL_PLANS.form_id 지연 업데이트
     * @return 생성된 formId
     */
    Long saveInputForm(Long userId, Long tripId, PlanInputFormSaveDto dto);

    /** GET /api/trips/{tripId} */
    PlanDetailResponseDto getPlanDetail(Long userId, Long tripId);

    /** GET /api/trips */
    List<PlanDetailResponseDto> getMyPlans(Long userId);

    /**
     * 이전 플랜 불러오기 (AUTO_LOADED)
     * 가장 최근 UI_CLICK / CHATBOT 폼을 복사해서 현재 플랜에 저장
     * @return formId (없으면 null)
     */
    Long loadPreviousPreference(Long userId, Long tripId);

    void updateInputForm(Long userId, Long tripId, java.util.Map<String, String> fields);

    Map<String, Object> getLatestPreference(Long userId);

    Map<String, Object> getInputFormMap(Long tripId);

}