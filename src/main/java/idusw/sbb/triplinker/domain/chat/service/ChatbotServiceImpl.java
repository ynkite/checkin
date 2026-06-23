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

/*
 * ============================================================================
 *  챗봇 AI 엔진 전환 가이드 (Groq/Gemini → Claude)
 * ----------------------------------------------------------------------------
 *  이 클래스는 챗봇 대화를 처리한다. 현재는 Claude(Anthropic)를 메인으로 사용하고,
 *  Claude 호출 실패 시 기존 Groq → Gemini 순서로 자동 폴백한다.
 *
 *  ※ 코드 안에서 아래 3가지 주석 표시를 찾아서, 쓰고 싶은 블록만 살리고
 *    나머지는 주석 처리하면 손쉽게 엔진을 갈아끼울 수 있다.
 *
 *    // 클로드 API 사용할 때          → Anthropic 정식 SDK(anthropicChatModel) 사용
 *    // open api 사용할 때            → Claude를 OpenAI 호환 엔드포인트로 사용 (선택)
 *    // 기존 API(그록 재미나이)사용할 때 → 원래 코드 그대로(Groq 메인 + Gemini 폴백)
 *
 *  ▶ Claude가 아예 안 될 때(키 미발급/장애 등):
 *    "// 클로드 API 사용할 때" 와 "// open api 사용할 때" 블록을 전부 주석 처리하고,
 *    "// 기존 API(그록 재미나이)사용할 때" 블록만 살리면 지금까지 쓰던 방식 그대로 동작한다.
 * ============================================================================
 */
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final TravelPlanRepository planRepository;

    // ── AI 클라이언트 ─────────────────────────────────────────────
    private final ChatClient claudeClient;    // Claude (메인)  ★ NEW
    private final ChatClient primaryClient;    // Groq (기존 메인 / 현재는 폴백)
    private final ChatClient fallbackClient;   // Gemini (기존 폴백)

    public ChatbotServiceImpl(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            TravelPlanRepository planRepository,

            // ===== // 클로드 API 사용할 때 (Anthropic 정식 SDK) =====
            // build.gradle 의 spring-ai-starter-model-anthropic 가 자동 생성하는 빈.
            // application.yml 에 spring.ai.anthropic.api-key / chat.options.model 설정 필요.
            @Qualifier("anthropicChatModel") ChatModel claudeModel,
            // ===== // 클로드 API 사용할 때 끝 =====

            // ===== // open api 사용할 때 (Claude를 OpenAI 호환 엔드포인트로 쓰는 경우) =====
            //  - 이 방식을 쓰려면 application.yml 에서 OpenAI starter 의 base-url 을
            //    https://api.anthropic.com/v1/ 로 바꾸고 model 을 claude-* 로 지정한 뒤,
            //    아래 @Qualifier("openAiChatModel") 빈을 claudeModel 대신 주입해 사용한다.
            //  - 별도의 OpenAI 호환 빈을 따로 등록했다면 그 빈 이름으로 바꿔 주입하면 된다.
            // @Qualifier("openAiChatModel") ChatModel claudeOpenAiModel,
            // ===== // open api 사용할 때 끝 =====

            // ===== // 기존 API(그록 재미나이)사용할 때 =====
            @Qualifier("openAiChatModel") ChatModel groqModel,      // Groq (OpenAI 호환)
            @Qualifier("googleGenAiChatModel") ChatModel geminiModel) {
        // ===== // 기존 API(그록 재미나이)사용할 때 끝 =====

        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.planRepository    = planRepository;

        // ===== // 클로드 API 사용할 때 =====
        this.claudeClient   = ChatClient.builder(claudeModel).build();
        // ===== // 클로드 API 사용할 때 끝 =====

        // ===== // open api 사용할 때 =====
        // (위 생성자 파라미터의 claudeOpenAiModel 주석을 풀고 아래 한 줄로 교체)
        // this.claudeClient   = ChatClient.builder(claudeOpenAiModel).build();
        // ===== // open api 사용할 때 끝 =====

        // ===== // 기존 API(그록 재미나이)사용할 때 =====
        this.primaryClient  = ChatClient.builder(groqModel).build();
        this.fallbackClient = ChatClient.builder(geminiModel).build();
        // ===== // 기존 API(그록 재미나이)사용할 때 끝 =====
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

        // 유저의 새로운 메시지 DB 저장
        ChatMessage userChat = new ChatMessage();
        userChat.setChatSession(session);
        userChat.setRole("USER");
        userChat.setContent(message);
        messageRepository.save(userChat);

        // 최근 20개 대화 역순으로 가져와서 시간순으로 뒤집기
        List<ChatMessage> recentHistory = messageRepository.findTop10ByChatSessionOrderByIdDesc(session);
        Collections.reverse(recentHistory);

        // Spring AI에게 넘겨줄 프롬프트 조립
        List<Message> promptMessages = new ArrayList<>();

        // 시스템 프롬프트 조립
        StringBuilder sysPrompt = new StringBuilder();

        // 절대 규칙 - PlanInputForm 유무와 관계없이 항상 적용
        sysPrompt.append("""
                "You are a Korean travel planner chatbot named 'TripLinker'. " +
                "ABSOLUTE RULE 1: Respond ONLY in Korean Hangul. NEVER use Chinese/Japanese characters or English words. " +
                "ABSOLUTE RULE 2: When the user says a budget amount, always compare it to the CURRENT budget value stored in the system. " +
                "If the new amount is LARGER than the current budget, say '예산을 늘리겠습니다.' " +
                "If the new amount is SMALLER than the current budget, say '예산을 줄이겠습니다.' " +
                "Current budget is explicitly shown in the travel info below — always use that as the reference.\\n"
                
                [절대 규칙 - 하드코딩, 예외 없음]
                 1. 해외 여행지 판단 규칙 (반드시 아래 조건을 모두 확인한 후 판단하세요):
                 - 트리거 조건: 사용자 메시지가 "일본 가고 싶어", "도쿄 여행", "오사카로 갈게요"처럼
                   해외 도시·국가를 **실제 여행 목적지**로 언급하는 경우에만 트리거하세요.
                 - 트리거 시 이 문장만 답하세요: "본 서비스는 국내 전용입니다. 국내 도시를 입력해 주세요"

                 ★절대 트리거 금지 예외 목록★ (아래 경우는 해외 여행지 언급이 아닙니다):
                 ① 음식·식당·요리 관련: "중국집", "일본식 라멘", "베트남 쌀국수", "중국 음식", "일식당",
                    "태국 음식", "인도 카레", "미국식 버거", "이탈리안 레스토랑" 등 음식 종류 표현
                 ② 국내 상호명·브랜드: "파리바게뜨", "뉴욕 야구단", "런던 베이글", "싱가포르 치킨라이스집" 등
                 ③ 일정 내 요청: "3일차 점심 중국집", "저녁에 일식", "2일차에 베트남 음식 먹고 싶어" 등
                    날짜/식사 맥락이 포함된 요청
                 ④ 비교·설명·질문: "중국처럼 넓은 곳", "일본 스타일로", "미국 드라마 같은 분위기" 등

                 판단이 애매할 때는 트리거하지 말고 정상 응답하세요.
                2. 예산이 0원 미만이거나 10,000,000원 초과인 경우
                   다른 설명 없이 반드시 이 문장만 답하세요: "예산을 다시 입력해 주세요"
                3. 여행과 무관한 대화(날씨, 요리, 정치 등)는 자연스럽게 여행 계획으로 유도하세요.
                4. 사용자가 대화 중 구체적인 요청을 하면 반드시 [EXTRA:라벨:값] 태그를 답변 끝에 추가하세요.
                  "- 추가 기준: 반드시 사용자가 보낸 메시지에 명확한 의도 표현이 있을 때만 추가합니다. " +
                  "AI의 추천/제안/답변 내용은 절대 EXTRA 태그로 추가하지 마세요. " +
                  "예를 들어 AI가 '흑돼지를 추천드려요'라고 했더라도 사용자가 '먹고 싶어'라고 직접 말하지 않으면 추가 금지입니다. " +
                  "사용자 메시지 원문에 '~하고 싶어', '~해줘', '~포함해줘', '~먹고 싶어', '~가고 싶어', '~원해', '~넣어줘' 등이 없으면 EXTRA 태그 사용 금지.",
                   - 라벨은 요청의 핵심을 2~6글자로 압축하세요.
                   - 예시: '2일차 점심에 흑돼지 먹고 싶어' → [EXTRA:2일차점심:흑돼지]
                           '카페는 꼭 넣어줘' → [EXTRA:필수포함:카페]
                           '마지막 날 바다 보고 싶어' → [EXTRA:마지막날:바다]
                           '아이 동반이라 실내 위주로 해줘' → [EXTRA:장소조건:실내 위주]
                           '숙소는 조용한 곳으로' → [EXTRA:숙소조건:조용한 곳]
                           '반려동물 동반 가능한 식당 위주로' → [EXTRA:식당조건:반려동물 동반 가능]
                   - 반드시 지켜야 할 금지 규칙:
                     ① 사용자가 직접 말하지 않은 내용은 절대 EXTRA 태그로 추가하지 마세요.
                     ② AI가 스스로 추천하거나 제안하는 내용에는 EXTRA 태그 금지.
                     ③ 이미 기존 항목(여행지/날짜/인원/예산/이동수단/숙소/스타일/밀도/반려동물)에 해당하면 UPDATE 태그를 사용하고 EXTRA는 쓰지 마세요.
                     ④ 값은 최대 15글자로 간결하게.
                """);

        // 여행 정보 주입 - PlanInputForm 데이터가 있을 때만 추가
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

        // ─────────────────────────────────────────────────────────────
        //  AI 호출
        //  현재 동작: Claude(메인) → 실패 시 Groq → 그래도 실패 시 Gemini 순서로 폴백
        // ─────────────────────────────────────────────────────────────
        String aiReply = null;

        // ===== // 클로드 API 사용할 때 (메인) =====
        //   open api 방식을 쓰는 경우에도 claudeClient 가 OpenAI 호환 빈으로
        //   주입되어 있으므로 이 블록을 그대로 사용하면 된다.
        try {
            System.out.println("🤖 Claude 챗봇 응답 생성 중...");
            aiReply = claudeClient.prompt()
                    .messages(promptMessages)
                    .call()
                    .content();
        } catch (Exception e) {
            System.out.println("⚠️ Claude 호출 실패, 기존 엔진(Groq→Gemini)으로 폴백: " + e.getMessage());
            aiReply = null;
        }
        // ===== // 클로드 API 사용할 때 끝 =====

        // ===== // 기존 API(그록 재미나이)사용할 때 (폴백 / 또는 단독 사용) =====
        //   ▶ Claude를 완전히 끄고 예전 방식만 쓰고 싶으면:
        //     위 "// 클로드 API 사용할 때" 블록을 통째로 주석 처리하고
        //     아래 try/catch 만 남기면 (Groq 메인 → Gemini 폴백) 원래대로 동작한다.
        if (aiReply == null) {
            try {
                aiReply = primaryClient.prompt()      // Groq
                        .messages(promptMessages)
                        .call()
                        .content();
            } catch (Exception e) {
                // Groq 실패(할당량 초과, 장애 등) → Gemini로 자동 전환
                aiReply = fallbackClient.prompt()     // Gemini
                        .messages(promptMessages)
                        .call()
                        .content();
            }
        }
        // ===== // 기존 API(그록 재미나이)사용할 때 끝 =====

        // AI 답변 DB 저장
        ChatMessage aiChat = new ChatMessage();
        aiChat.setChatSession(session);
        aiChat.setRole("AI");
        aiChat.setContent(aiReply);
        messageRepository.save(aiChat);

        return aiReply;

    }
    @Override
    @Transactional
    public void saveSystemMessage(Long sessionId, String message) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대화방입니다."));

        ChatMessage systemChat = new ChatMessage();
        systemChat.setChatSession(session);
        systemChat.setRole("SYSTEM");
        systemChat.setContent(message);
        messageRepository.save(systemChat);
    }

}