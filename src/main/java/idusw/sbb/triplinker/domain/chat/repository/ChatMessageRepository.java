package idusw.sbb.triplinker.domain.chat.repository;

import idusw.sbb.triplinker.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
}