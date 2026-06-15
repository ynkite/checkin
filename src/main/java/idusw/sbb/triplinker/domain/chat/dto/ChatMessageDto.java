package idusw.sbb.triplinker.domain.chat.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageDto {
    private Long sessionId;   // 대화방 번호
    private String message; // 유저가 보낸 질문
    private Boolean isSystem;
}