//    여행 플랜 생성 요청 DTO
//    POST /api/trips 호출 시 클라이언트가 전달하는 요청 바디.
//    여행지, 출발일, 귀환일 등 TRAVEL_PLANS 생성에 필요한 최소 정보를 담는다.

package idusw.sbb.triplinker.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


//POST /api/trips  요청 바디
//TRAVEL_PLANS 생성 시 사용 (form_id 는 서버에서 NULL 처리)

@Getter
@NoArgsConstructor
public class PlanCreateDto {

    @NotBlank(message = "여행지를 입력해주세요.")
    private String destination;

    @NotNull(message = "출발일을 입력해주세요.")
    private LocalDate startDate;

    @NotNull(message = "귀환일을 입력해주세요.")
    private LocalDate endDate;

    private String title;         // nullable — 나중에 AI가 자동 생성
    private int isPublic = 0;     // 기본 비공개
    private String status = "DRAFT";
}