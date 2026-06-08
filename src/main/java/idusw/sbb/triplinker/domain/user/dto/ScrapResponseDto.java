/**
 * 장소 스크랩 응답 DTO
 * - 마이페이지에서 사용자가 스크랩한 장소 목록을 보여줄 때 사용한다.
 * - 장소 ID, 이름, 주소, 평점, 썸네일 등 화면 표시용 정보를 담는다.
 * - 현재 Place 도메인이 아직 구현되지 않았기 때문에 placeId와 category만 응답한다.
 */
package idusw.sbb.triplinker.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScrapResponseDto {
    private Long scrapId;
    private Long placeId;
    private String category;
}