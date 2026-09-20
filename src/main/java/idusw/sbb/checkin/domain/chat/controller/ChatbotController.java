package idusw.sbb.checkin.domain.chat.controller;

import idusw.sbb.checkin.domain.chat.dto.ChatMessageDto;
import idusw.sbb.checkin.domain.chat.service.ChatbotService;
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

        // reply 는 주행 화면 음성 안내가 읽는 이름이다. response 와 같은 값이다
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of("response", responseMessage, "reply", responseMessage)
        ));
    }
}