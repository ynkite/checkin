package idusw.sbb.triplinker.domain.chat.repository;

import idusw.sbb.triplinker.domain.chat.entity.ChatMessage;
import idusw.sbb.triplinker.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 최신 대화 기록을 20개만 내림차순(가장 최근 것부터)으로 가져옵니다.
    List<ChatMessage> findTop10ByChatSessionOrderByIdDesc(ChatSession chatSession);
}