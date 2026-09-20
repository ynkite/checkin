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

    /* 소리로 답할 물음인가. 실시간 「말로 하기」가 넘긴 물음이면 true 로 보낸다.
       읽어 주는 답은 길면 끝까지 들을 수 없다 — 한두 문장으로 줄인다. */
    private Boolean brief;
}
