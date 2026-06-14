//    여행 취향 폼 저장 요청 DTO
//    POST /api/trips/{tripId}/input-form 호출 시 클라이언트가 전달하는 요청 바디.
//    플래너 STEP 1~2의 전체 입력값(이동수단, 숙소, 동행자, 여행 스타일, 예산 등)을 담는다.

package idusw.sbb.triplinker.domain.plan.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST /api/trips/{tripId}/input-form  요청 바디
 * PLAN_INPUT_FORM 저장 시 사용
 *
 * travel_styles / dietary_info / accommodation_options 는
 * 프론트에서 JSON 배열 문자열로 전달 (예: "[\"가성비\",\"힐링\"]")
 */
@Getter
@NoArgsConstructor
public class PlanInputFormSaveDto {

    // ── STEP 1 에서 수집 ───────────────────────────
    private String departure;
    private String transportType;       // CAR / PUBLIC
    private String accommodationType;
    private String accommodationOptions; // JSON 배열 문자열
    private String companionType;       // SOLO / COUPLE / FAMILY / FRIENDS
    private Integer companionCount;

    // ── STEP 2 에서 수집 ───────────────────────────
    private String travelStyles;        // JSON 배열 문자열
    private String dietaryInfo;         // JSON 배열 문자열
    private int hasInfant = 0;
    private int hasPet = 0;
    private String scheduleDensity;     // DENSE / RELAXED
    private Long budget;
    private String extraNotes;          // 기타 특수 조건 등 추가 입력(JSON 배열 문자열)
    private String preferenceSource = "UI_CLICK"; // UI_CLICK / CHATBOT / AUTO_LOADED
    private Long loadedFromPlanId;      // 이전 플랜 불러오기 시 원본 플랜 ID
}
