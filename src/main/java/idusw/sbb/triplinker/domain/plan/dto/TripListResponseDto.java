//내 여행 기록 목록 응답 DTO
//마이페이지에서 여행 기록 카드 형태로 보여줄 데이터를 담는다.
//여행 제목, 기간 메타 정보, 예산, 여행 상태를 전달한다.

package idusw.sbb.triplinker.domain.plan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripListResponseDto {
    private Long id;
    private String title;       // "제주도 3박4일 힐링 여행"
    private String meta;        // "2026.06.14~06.17 · 2인"
    private String budget;      // "₩404,000"
    private String status;      // "UPCOMING" 또는 "PAST"


    private String startDate;
    private String endDate;
    private String destination;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

}