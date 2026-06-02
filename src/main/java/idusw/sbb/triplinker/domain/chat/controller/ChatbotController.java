package idusw.sbb.triplinker.domain.chat.controller;

import idusw.sbb.triplinker.domain.chat.dto.ChatMessageDto;
import idusw.sbb.triplinker.domain.chat.dto.ChatSessionResponseDto;
import idusw.sbb.triplinker.domain.chat.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/send")
    public ResponseEntity<ChatSessionResponseDto> sendMessage(@RequestBody ChatMessageDto request) {
        // 💡 1. 데이터가 잘 들어오는지 콘솔에 찍어봅니다!
        System.out.println(">>> 프론트에서 받은 메시지: " + request.getUserMessage());
        System.out.println(">>> 프론트에서 받은 세션ID: " + request.getSessionId());

        ChatSessionResponseDto response = chatbotService.processMessage(
                request.getSessionId(),
                request.getUserMessage()
        );

        return ResponseEntity.ok(response);
    }
    // 404 에러를 막기 위한 임시 껍데기 메서드들
    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions() {
        return ResponseEntity.ok().build(); // 아무것도 안 하고 200 OK만 보냄
    }

    @GetMapping("/message")
    public ResponseEntity<?> getMessages() {
        return ResponseEntity.ok().build();
    }
    @PostMapping("/sessions") // 💡 추가
    public ResponseEntity<?> createSession() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/message") // 💡 추가
    public ResponseEntity<?> sendMessageToMessage() {
        return ResponseEntity.ok().build();
    }
}