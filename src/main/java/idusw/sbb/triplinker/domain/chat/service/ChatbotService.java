package idusw.sbb.triplinker.domain.chat.service;

import idusw.sbb.triplinker.domain.chat.dto.ChatSessionResponseDto;

public interface ChatbotService {
    ChatSessionResponseDto processMessage(Long sessionId, String userMessage);
}