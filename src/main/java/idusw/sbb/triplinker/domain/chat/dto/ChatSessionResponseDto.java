package idusw.sbb.triplinker.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatSessionResponseDto {
    private Long sessionId;
    private String reply; // AI의 답변
}