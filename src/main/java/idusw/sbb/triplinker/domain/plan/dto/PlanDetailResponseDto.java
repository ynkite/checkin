//    여행 플랜 상세 조회 응답 DTO
//    GET /api/trips/{tripId} 응답 바디.
//    TravelPlan과 연결된 PlanInputForm 데이터를 하나의 객체로 조합해서 반환한다.
//    취향 폼이 아직 저장되지 않은 경우 폼 관련 필드는 null로 반환한다.

package idusw.sbb.triplinker.domain.plan.dto;


import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GET /api/trips/{tripId}  응답 바디
 * TravelPlan + PlanInputForm 을 조합해서 반환
 */
@Getter
public class PlanDetailResponseDto {

    private final Long tripId;
    private final String title;
    private final String destination;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int isPublic;
    private final String status;
    private final LocalDateTime createdAt;

    // 취향 폼 (없을 수 있음)
    private final Long formId;
    private final String departure;
    private final String transportType;
    private final String accommodationType;
    private final String accommodationOptions;
    private final String companionType;
    private final Integer companionCount;
    private final String travelStyles;
    private final String dietaryInfo;
    private final int hasInfant;
    private final int hasPet;
    private final String scheduleDensity;
    private final Long budget;
    private final String preferenceSource;

    public PlanDetailResponseDto(TravelPlan plan) {
        this.tripId      = plan.getId();
        this.title       = plan.getTitle();
        this.destination = plan.getDestination();
        this.startDate   = plan.getStartDate();
        this.endDate     = plan.getEndDate();
        this.isPublic    = plan.getIsPublic();
        this.status      = plan.getStatus();
        this.createdAt   = plan.getCreatedAt();

        PlanInputForm f = plan.getForm();
        if (f != null) {
            this.formId               = f.getId();
            this.departure            = f.getDeparture();
            this.transportType        = f.getTransportType();
            this.accommodationType    = f.getAccommodationType();
            this.accommodationOptions = f.getAccommodationOptions();
            this.companionType        = f.getCompanionType();
            this.companionCount       = f.getCompanionCount();
            this.travelStyles         = f.getTravelStyles();
            this.dietaryInfo          = f.getDietaryInfo();
            this.hasInfant            = f.getHasInfant();
            this.hasPet               = f.getHasPet();
            this.scheduleDensity      = f.getScheduleDensity();
            this.budget               = f.getBudget();
            this.preferenceSource     = f.getPreferenceSource();
        } else {
            this.formId = null;
            this.departure = null; this.transportType = null;
            this.accommodationType = null; this.accommodationOptions = null;
            this.companionType = null; this.companionCount = null;
            this.travelStyles = null; this.dietaryInfo = null;
            this.hasInfant = 0; this.hasPet = 0;
            this.scheduleDensity = null; this.budget = null;
            this.preferenceSource = null;
        }
    }
}