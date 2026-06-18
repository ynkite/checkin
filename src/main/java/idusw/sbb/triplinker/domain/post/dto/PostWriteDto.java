package idusw.sbb.triplinker.domain.post.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PostWriteDto {

    private Long planId;
    private String title;
    private String content;
    // 여행 취향 태그 (JSON 문자열)
    private String styleTags;
    private String category = "ROUTE";
    private boolean isPublic = true;

    // 이미지 업로드 후 받은 URL 목록
    private List<String> imageUrls;
}