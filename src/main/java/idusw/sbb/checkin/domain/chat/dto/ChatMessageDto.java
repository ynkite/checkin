package idusw.sbb.checkin.domain.chat.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageDto {
    private Long sessionId;   // 대화방 번호
    private String message;   // 사용자가 보낸 질문
    private Boolean isSystem;
    private Double lat;       // 기기 위치. 화면이 못 받았으면 비워 둔다
    private Double lng;
}
