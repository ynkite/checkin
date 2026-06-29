//    여행 취향 입력 폼 저장 기능
//    PLAN_INPUT_FORM 테이블과 매핑되는 JPA 엔티티 클래스.
//    플래너 STEP 1~2에서 사용자가 입력한 출발지, 이동수단, 숙소 형태, 동행자 유형,
//    여행 스타일, 식이 정보, 일정 밀도, 예산 등 취향 데이터 전체를 저장한다.
//    travel_styles, dietary_info, accommodation_options는 JSON 배열 문자열로 저장한다.


package idusw.sbb.triplinker.domain.plan.entity;


import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


//  PLAN_INPUT_FORM 테이블 매핑 엔티티
//  TravelPlan 과 1:1 대응.
//  travel_styles, dietary_info, accommodation_options 는
//  JSON 배열 문자열로 저장 (예: ["가성비","힐링"])

@Entity
@Table(name = "PLAN_INPUT_FORM")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlanInputForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //  연관 관계
//    TRAVEL_PLANS.id FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private TravelPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    //  STEP 1 - 기본 정보
    @Column(length = 100)
    private String departure;

//    이동 수단
    @Column(name = "transport_type", length = 20)
    private String transportType;

//   숙소형태
    @Column(name = "accommodation_type", length = 50)
    private String accommodationType;

    // 동행자 유형
    @Column(name = "companion_type", length = 20)
    private String companionType;

    @Column(name = "companion_count")
    private Integer companionCount;

    // STEP 2 - 취향 설정
    // 여행 스타일 (JSON 배열 문자열)
    @Column(name = "travel_styles", length = 255)
    private String travelStyles;

    // 식이 정보  (JSON 배열 문자열)
    @Column(name = "dietary_info", length = 255)
    private String dietaryInfo;

    // 특수 조건(유아 동반)
    @Column(name = "has_infant")
    @Builder.Default
    private int hasInfant = 0;

    // 특수 조건(반려동물 동반)
    @Column(name = "has_pet")
    @Builder.Default
    private int hasPet = 0;

    // 일정 밀도
    @Column(name = "schedule_density", length = 20)
    private String scheduleDensity;

    // 숙소 세부 옵션 (JSON 배열 문자열)
    @Column(name = "accommodation_options", length = 255)
    private String accommodationOptions;

//    총 예산 — CHECK(budget >= 0 AND budget <= 10000000)
    @Column(columnDefinition = "BIGINT CHECK (budget >= 0 AND budget <= 10000000)")
    private Long budget;

    // UI_CLICK / CHATBOT / AUTO_LOADED
    @Column(name = "preference_source", length = 20)
    @Builder.Default
    private String preferenceSource = "UI_CLICK";


    @Column(name = "loaded_from_plan_id")
    private Long loadedFromPlanId;

    //AI 챗봇 EXTRA 태그 기타 사항 (JSON 배열 문자열)
    @Column(name = "extra_notes", columnDefinition = "TEXT")
    private String extraNotes;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 챗봇 수동 수정 시 즉시 Persist
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 비즈니스 메서드

    // 챗봇이 필드 값을 수정할 때 사용 (preferenceSource → CHATBOT)
    public void updateByChat(String field, String value) {
        switch (field) {
            case "destination"           -> {}  // TravelPlan 쪽 수정
            case "transportType"         -> this.transportType = value;
            case "accommodationType"     -> this.accommodationType = value;
            case "accommodationOptions"  -> this.accommodationOptions = value;
            case "travelStyles"          -> this.travelStyles = value;
            case "dietaryInfo"           -> this.dietaryInfo = value;
            case "scheduleDensity"       -> this.scheduleDensity = value;
            case "budget"         -> this.budget = Long.parseLong(value.isEmpty() ? "0" : value);
            case "companionType"  -> this.companionType = value;
            case "companionCount" -> { if (!value.isEmpty()) this.companionCount = Integer.parseInt(value); }
            case "hasPet"         -> this.hasPet = "동반".equals(value) ? 1 : 0;
            case "extraNotes"     -> this.extraNotes = value;
        }
        this.preferenceSource = "CHATBOT";
    }
}