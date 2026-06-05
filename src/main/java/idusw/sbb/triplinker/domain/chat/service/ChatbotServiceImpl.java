package idusw.sbb.triplinker.domain.chat.service;

import idusw.sbb.triplinker.domain.chat.entity.ChatMessage;
import idusw.sbb.triplinker.domain.chat.entity.ChatSession;
import idusw.sbb.triplinker.domain.chat.repository.ChatMessageRepository;
import idusw.sbb.triplinker.domain.chat.repository.ChatSessionRepository;

import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
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
    private final TravelPlanRepository planRepository;
    private final ChatClient chatClient;


    public ChatbotServiceImpl(ChatSessionRepository sessionRepository,
                              ChatMessageRepository messageRepository,
                              TravelPlanRepository planRepository,

                              // ollama
//                              @Qualifier("ollamaChatModel") ChatModel chatModel)

                              // chatgpt
//                              @Qualifier("openAiChatModel") ChatModel chatModel)

                              // claude
//                              @Qualifier("anthropicChatModel") ChatModel chatModel)

                              // gemini
                              @Qualifier("googleGenAiChatModel") ChatModel chatModel)

    {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.planRepository = planRepository;
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

        //  유저의 새로운 메시지 DB 저장
        ChatMessage userChat = new ChatMessage();
        userChat.setChatSession(session);
        userChat.setRole("USER");
        userChat.setContent(message);
        messageRepository.save(userChat);

        //  [슬라이딩 윈도우] 최근 20개 대화 역순으로 가져와서 시간순으로 뒤집기
        List<ChatMessage> recentHistory = messageRepository.findTop20ByChatSessionOrderByIdDesc(session);
        Collections.reverse(recentHistory);

        //  Spring AI에게 넘겨줄 프롬프트 조립
        List<Message> promptMessages = new ArrayList<>();

        // 시스템 프롬프트: DB에서 여행 취향 데이터를 꺼내어 주입
        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("당신은 친절하고 전문적인 대한민국 여행 플래너 챗봇 'TripLinker'입니다. 반드시 '한국어'로 대답하세요.\n\n");

        if (session.getPlanId() != null) {
            planRepository.findById(session.getPlanId()).ifPresent(plan -> {
                PlanInputForm form = plan.getForm();
                if (form != null) {
                    sysPrompt.append(String.format("""
                                당신은 'TripLinker'의 전문 AI 여행 플래너입니다.
                                사용자는 이미 아래와 같은 여행 기본 정보와 세부 취향을 설정했습니다.
                                이 대화방의 목적은 '전체 세부 일정(Day 1, Day 2...)을 짜주는 것'이 아닙니다.
                                사용자가 최종 일정을 생성하기 전에, 입력된 정보를 바탕으로 **추가적인 요구사항(인원 변경, 예산 조정, 특정 명소/맛집 추가 등)을 상담하고 조율하는 역할**만 수행하세요.
                                
                                [여행 기본 정보]
                                - 여행지: %s
                                - 일정: %s ~ %s
                                - 예산: %d원
                                - 인원: %s (%d명)
                                
                                [세부 취향 및 조건]
                                - 이동 수단: %s
                                - 선호 숙소: %s (옵션: %s)
                                - 여행 스타일: %s
                                - 식이 정보(알러지/비건 등): %s
                                - 일정 밀도: %s (이 밀도에 맞춰 장소 개수를 조절할 것)
                                - 특수 조건: 유아 동반(%s), 반려동물 동반(%s)
                                
                                [대화 규칙]
                                1. 사용자는 이미 화면에서 모든 정보를 입력하고 왔으므로, 위 정보를 다시 입력하라고 묻지 마세요.
                                2. 절대 먼저 구체적인 'Day 1, Day 2...' 형태의 추천 일정표를 짜서 출력하지 마세요.
                                3. 대화를 시작할 때, 입력된 핵심 정보를 가볍게 짚어준 뒤 "이대로 일정을 생성할까요? 아니면 예산 조정, 인원 변경, 꼭 가고 싶은 장소 추가 등 더 반영하고 싶은 사항이 있으신가요?"라고 물어보며 상담을 유도하세요.
                                4. 사용자가 특정 조건(예: 예산 줄이기, 반려견 식당 추가) 수정을 요청하면, 그 요구에 맞춰 여행 방향을 어떻게 수정하면 좋을지 친절하게 대답해 주세요.
                               
                                [시스템 태그 규칙 - 매우 중요]
                                 사용자가 인원, 예산, 이동수단 등 특정 조건의 변경을 요청하여 당신이 이를 수락하고 반영할 경우, 답변 텍스트 맨 마지막에 반드시 아래 형식의 태그를 숨겨서 출력하세요. 화면 UI를 업데이트하기 위한 용도입니다.
                                 형식: [UPDATE:항목코드:새로운값]
                                 - 항목코드 종류: DEST(여행지), DATE(날짜), PEOPLE(인원), BUDGET(예산), TRANS(이동수단), ACC(숙소), STYLE(여행스타일), DENSITY(일정밀도), PET(반려동물 등)
                                 - 예시: 사용자가 "인원을 5명으로 바꿔줘"라고 하면 답변 끝에 [UPDATE:PEOPLE:5인] 이라고 적습니다. "예산을 400만원으로 할게"라고 하면 [UPDATE:BUDGET:₩4,000,000] 이라고 적습니다. 다중 변경 시 태그를 여러 개 적습니다.
                                """,
                            plan.getDestination(), plan.getStartDate(), plan.getEndDate(), form.getBudget(),
                            form.getCompanionType(), form.getCompanionCount(),
                            form.getTransportType(), form.getAccommodationType(), form.getAccommodationOptions(),
                            form.getTravelStyles(), form.getDietaryInfo(), form.getScheduleDensity(),
                            form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X"
                    ));
                }
            });
        }

        promptMessages.add(new SystemMessage(sysPrompt.toString()));

        // 최근 20개의 대화 기록 얹어주기
        for (ChatMessage chat : recentHistory) {
            if ("USER".equals(chat.getRole())) {
                promptMessages.add(new UserMessage(chat.getContent()));
            } else if ("AI".equals(chat.getRole())) {
                promptMessages.add(new AssistantMessage(chat.getContent()));
            }
        }

        // AI 호출
        String aiReply = chatClient.prompt()
                .messages(promptMessages)
                .call()
                .content();

        // AI 답변 DB 저장
        ChatMessage aiChat = new ChatMessage();
        aiChat.setChatSession(session);
        aiChat.setRole("AI");
        aiChat.setContent(aiReply);
        messageRepository.save(aiChat);

        return aiReply;
    }
}