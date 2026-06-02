package idusw.sbb.triplinker.domain.chat.service;

import idusw.sbb.triplinker.domain.chat.dto.ChatSessionResponseDto;
import idusw.sbb.triplinker.domain.chat.entity.ChatMessage;
import idusw.sbb.triplinker.domain.chat.entity.ChatSession;
import idusw.sbb.triplinker.domain.chat.repository.ChatMessageRepository;
import idusw.sbb.triplinker.domain.chat.repository.ChatSessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatClient chatClient;

    // ChatClient.Builder를 주입받아 AI 클라이언트를 생성합니다.
    public ChatbotServiceImpl(ChatSessionRepository sessionRepository,
                              ChatMessageRepository messageRepository,
                              @Qualifier("ollamaChatModel") ChatModel chatModel) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    @Transactional
    public ChatSessionResponseDto processMessage(Long sessionId, String userMessage) {

        // 1. 세션(대화방) 찾기 또는 새로 만들기
        ChatSession session;
        if (sessionId == null) {
            session = new ChatSession();
            session.setTitle("새로운 여행 플랜 대화");
            session = sessionRepository.save(session);
        } else {
            session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대화방입니다."));
        }

        // 2. 유저 메시지 DB 저장
        ChatMessage userChat = new ChatMessage();
        userChat.setChatSession(session);
        userChat.setRole("USER");
        userChat.setContent(userMessage);
        messageRepository.save(userChat);

        // 3. AI에게 질문 던지기 (Spring AI)
        String systemPrompt = "너는 친절하고 전문적인 대한민국 여행 플래너 챗봇이야. 사용자의 질문에 짧고 명확하게 대답해줘. 반드시 '한국어(Korean)'로만 대답해야 해!";
        String aiReply = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        // 4. AI 답변 DB 저장
        ChatMessage aiChat = new ChatMessage();
        aiChat.setChatSession(session);
        aiChat.setRole("AI");
        aiChat.setContent(aiReply);
        messageRepository.save(aiChat);

        // 5. 프론트엔드로 DTO 응답
        return ChatSessionResponseDto.builder()
                .sessionId(session.getId())
                .reply(aiReply)
                .build();
    }
}