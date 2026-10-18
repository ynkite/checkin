package idusw.sbb.checkin.domain.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private Long planId;

    // 대화방을 만든 회원. 남의 대화방에 말을 넣지 못하게 한다 (예전 방은 비어 있어 아무도 못 쓴다)
    private Long userId;

    private LocalDateTime createdAt = LocalDateTime.now();
}