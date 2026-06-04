package idusw.sbb.triplinker.domain.chat.service;

import idusw.sbb.triplinker.domain.chat.entity.ChatMessage;
import idusw.sbb.triplinker.domain.chat.entity.ChatSession;
import idusw.sbb.triplinker.domain.chat.repository.ChatMessageRepository;
import idusw.sbb.triplinker.domain.chat.repository.ChatSessionRepository;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatClient chatClient;

    public ChatbotServiceImpl(ChatSessionRepository sessionRepository,
                              ChatMessageRepository messageRepository,

                              // ollama
                              @Qualifier("ollamaChatModel") ChatModel chatModel)

                              // chatgpt
//                              @Qualifier("openAiChatModel") ChatModel chatModel)

                              // claude
//                              @Qualifier("anthropicChatModel") ChatModel chatModel)

                              // gemini
//                              @Qualifier("googleGenAiChatModel") ChatModel chatModel)

    {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    // API 명세: 세션 생성
    @Override
    @Transactional
    public Long createSession(Long planId) {
        ChatSession session = new ChatSession();
        session.setTitle("새로운 여행 플랜 대화");
        session.setPlanId(planId);
        session = sessionRepository.save(session);
        return session.getId();
    }

    // API 명세: 메시지 전송 및 기억력 유지
    @Override
    @Transactional
    public String processMessage(Long sessionId, String message) {

        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대화방입니다."));

        // 1. 유저의 새로운 메시지 DB 저장
        ChatMessage userChat = new ChatMessage();
        userChat.setChatSession(session);
        userChat.setRole("USER");
        userChat.setContent(message);
        messageRepository.save(userChat);

        // 2. [슬라이딩 윈도우] 최근 20개 대화 역순으로 가져와서 시간순으로 뒤집기
        List<ChatMessage> recentHistory = messageRepository.findTop20ByChatSessionOrderByIdDesc(session);
        Collections.reverse(recentHistory);

        // 3. Spring AI에게 넘겨줄 프롬프트 조립
        List<Message> promptMessages = new ArrayList<>();

        // 시스템 프롬프트 (여행 조건 주입 안 함! 그냥 기본 역할만 부여)
        promptMessages.add(new SystemMessage("너는 친절하고 전문적인 대한민국 여행 플래너 챗봇이야. 반드시 '한국어(Korean)'로 대답해!"));

        // 최근 20개의 대화 기록 얹어주기
        for (ChatMessage chat : recentHistory) {
            if ("USER".equals(chat.getRole())) {
                promptMessages.add(new UserMessage(chat.getContent()));
            } else if ("AI".equals(chat.getRole())) {
                promptMessages.add(new AssistantMessage(chat.getContent()));
            }
        }

        // 4. AI 호출
        String aiReply = chatClient.prompt()
                .messages(promptMessages)
                .call()
                .content();

        // 5. AI 답변 DB 저장
        ChatMessage aiChat = new ChatMessage();
        aiChat.setChatSession(session);
        aiChat.setRole("AI");
        aiChat.setContent(aiReply);
        messageRepository.save(aiChat);

        return aiReply;
    }
}