package idusw.sbb.checkin.domain.chat.service;

public interface ChatbotService {
    Long createSession(Long planId); // 세션 생성

    /** 메시지 전송 및 답변 반환. lat·lng 는 기기 위치이고 모르면 null 이다. */
    String processMessage(Long sessionId, String message, Double lat, Double lng);

    default String processMessage(Long sessionId, String message) {
        return processMessage(sessionId, message, null, null);
    }

    void saveSystemMessage(Long sessionId, String message);
}
