package idusw.sbb.checkin.domain.chat.controller;

import idusw.sbb.checkin.domain.chat.dto.ChatMessageDto;
import idusw.sbb.checkin.domain.chat.service.ChatbotService;
import idusw.sbb.checkin.domain.chat.service.ReplyText;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    // API 챗봇 세션 생성 (POST /api/chat/sessions)
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

    // 작성 중인 플랜을 다시 열 때 대화를 되살린다. 전에는 GET 이 없어 405 였다
    @GetMapping("/sessions")
    public ResponseEntity<?> mySessions() {
        return ResponseEntity.ok(Map.of("success", true, "data", chatbotService.mySessions()));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> messages(@PathVariable Long sessionId) {
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("messages", chatbotService.messages(sessionId))));
    }

    // API AI 채팅 메시지 전송 (POST /api/chat/message)
    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessageDto request) {

        // isSystem=true 이면 AI 호출 없이 SYSTEM 메시지만 DB 저장
        if (Boolean.TRUE.equals(request.getIsSystem())) {
            chatbotService.saveSystemMessage(
                    request.getSessionId(),
                    request.getMessage()
            );
            return ResponseEntity.ok(Map.of("success", true, "data", Map.of("response", "")));
        }

        // 범위를 벗어난 좌표는 위치를 모르는 것과 같게 다룬다
        boolean validPos = request.getLat() != null && request.getLng() != null
                && Math.abs(request.getLat()) <= 90 && Math.abs(request.getLng()) <= 180
                && !(request.getLat() == 0 && request.getLng() == 0);
        String responseMessage = chatbotService.processMessage(
                request.getSessionId(),
                request.getMessage(),
                validPos ? request.getLat() : null,
                validPos ? request.getLng() : null,
                Boolean.TRUE.equals(request.getBrief())
        );

        // reply 는 음성 안내가 읽는다. 화면이 읽는 태그를 뗀 것 말고는 response 와 같다
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of("response", responseMessage, "reply", ReplyText.untag(responseMessage))
        ));
    }
}