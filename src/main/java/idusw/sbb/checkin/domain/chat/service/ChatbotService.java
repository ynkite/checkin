package idusw.sbb.checkin.domain.chat.service;

public interface ChatbotService {
    Long createSession(Long planId); // 세션 생성

    /** 메시지 전송 및 답변 반환. lat·lng 는 기기 위치이고 모르면 null 이다. */
    String processMessage(Long sessionId, String message, Double lat, Double lng, boolean brief);

    /** 화면에서 온 물음. 길이를 줄이지 않는다 */
    default String processMessage(Long sessionId, String message, Double lat, Double lng) {
        return processMessage(sessionId, message, lat, lng, false);
    }

    default String processMessage(Long sessionId, String message) {
        return processMessage(sessionId, message, null, null);
    }

    void saveSystemMessage(Long sessionId, String message);

    /** 내 대화방 목록 — 플랜을 이어 쓸 때 어느 방을 다시 열지 고른다 */
    java.util.List<java.util.Map<String, Object>> mySessions();

    /** 내 대화방의 말 전부, 오래된 것부터 */
    java.util.List<java.util.Map<String, Object>> messages(Long sessionId);
}
