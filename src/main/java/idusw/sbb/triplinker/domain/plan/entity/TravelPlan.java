//    여행 플랜 생성 기능
//    TRAVEL_PLANS 테이블과 매핑되는 JPA 엔티티 클래스.
//    플랜의 여행지, 날짜, 공개 여부, 상태(DRAFT/CONFIRMED) 등 기본 정보를 저장한다.
//    취향 입력 폼(PlanInputForm)과 순환 FK 관계를 가지며,
//    처음 생성 시 form_id를 NULL로 두고 이후 linkInputForm()으로 연결하는 지연 업데이트 패턴을 사용한다.

package idusw.sbb.triplinker.domain.plan.entity;

import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;


//  TRAVEL_PLANS 테이블 매핑 엔티티
//
//  순환 FK 처리 전략:
//    TravelPlan INSERT (form_id = NULL)
//    PlanInputForm INSERT (plan_id = travelPlan.id)
//    TravelPlan UPDATE (form_id = planInputForm.id) ← 지연 업데이트

@Entity
@Table(name = "TRAVEL_PLANS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Setter
public class TravelPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


//     취향 입력 폼 FK (순환 FK — 처음엔 NULL, PlanInputForm 저장 후 지연 업데이트)
//     TravelPlanServiceImpl.linkInputForm() 에서 UPDATE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = true)
    private PlanInputForm form;

    // 기본 정보
    @Column(length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // 공개 여부 (0: 비공개, 1: 공개)
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private int isPublic = 0;

    // 스크랩 원본 플랜 ID (복사본에만 기록)
    @Column(name = "scraped_from_plan_id")
    private Long scrapedFromPlanId;

    // 플랜 상태 (DRAFT / CONFIRMED)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    // 교통비 재계산 대기 플래그 (0: 불필요, 1: 재계산 필요)
    @Column(name = "route_recalc_needed", nullable = false)
    @Builder.Default
    private int routeRecalcNeeded = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    @Column(columnDefinition = "TEXT")
    private String routeJson; //여기에 AI가 생성한 JSON 문자열을 통째로 저장

    // ★수정 중(draft) 일정 JSON. 마이페이지에서 확정본을 수정할 때는 여기에만 저장하고,
    //   '확정(FIXED)'을 누를 때 routeJson(확정본)으로 승격한다.
    //   미확정 상태로 종료/로그아웃해도 routeJson(직전 확정본)은 보존된다.
    @Column(name = "draft_route_json", columnDefinition = "TEXT")
    private String draftRouteJson;


    // 비즈니스 메서드

    // 지연 업데이트: PlanInputForm 저장 후 form_id 연결
    public void linkInputForm(PlanInputForm form) {
        this.form = form;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void markRouteRecalcNeeded() {
        this.routeRecalcNeeded = 1;
    }

    public void clearRouteRecalcFlag() {
        this.routeRecalcNeeded = 0;
    }

    // ★확정(FIXED): draft가 있으면 그것을 확정본(routeJson)으로 승격하고 draft를 비운다.
    public void confirmDraft() {
        if (this.draftRouteJson != null && !this.draftRouteJson.isBlank()) {
            this.routeJson = this.draftRouteJson;
        }
        this.draftRouteJson = null;
        this.status = "FIXED";
    }

    // ★미확정 수정 폐기: draft를 버리고 직전 확정본(routeJson)을 유지한다.
    public void discardDraft() {
        this.draftRouteJson = null;
    }

}