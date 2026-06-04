package idusw.sbb.triplinker.domain.chat.controller;

import idusw.sbb.triplinker.domain.chat.dto.ChatMessageDto;
import idusw.sbb.triplinker.domain.chat.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    // API: 챗봇 세션 생성 (POST /api/chat/sessions)
    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody Map<String, Long> request) {
        Long planId = request.get("planId");
        Long sessionId = chatbotService.createSession(planId);

        // 명세서 모양: {"success":true, "data":{"sessionId":1}}
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of("sessionId", sessionId)
        ));
    }

    // API: AI 채팅 메시지 전송 (POST /api/chat/message)
    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessageDto request) {
        String responseMessage = chatbotService.processMessage(
                request.getSessionId(),
                request.getMessage()
        );

        // 명세서 모양: {"success":true, "data":{"response":"..."}}
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of("response", responseMessage)
        ));
    }
}