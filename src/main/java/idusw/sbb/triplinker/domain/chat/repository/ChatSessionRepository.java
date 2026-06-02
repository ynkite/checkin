package idusw.sbb.triplinker.domain.chat.repository;

import idusw.sbb.triplinker.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}