package idusw.sbb.triplinker.domain.chat.repository;

import idusw.sbb.triplinker.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}