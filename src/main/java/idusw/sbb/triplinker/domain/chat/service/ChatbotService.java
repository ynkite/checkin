package idusw.sbb.triplinker.domain.chat.service;

public interface ChatbotService {
    Long createSession(Long planId); // 세션 생성
    String processMessage(Long sessionId, String message); // 메시지 전송 및 답변 반환
    void saveSystemMessage(Long sessionId, String message);
}