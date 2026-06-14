package idusw.sbb.triplinker.domain.chat.repository;

import idusw.sbb.triplinker.domain.chat.entity.ChatMessage;
import idusw.sbb.triplinker.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 최신 대화 기록을 10개만 가져옴
    List<ChatMessage> findTop10ByChatSessionOrderByIdDesc(ChatSession chatSession);
}