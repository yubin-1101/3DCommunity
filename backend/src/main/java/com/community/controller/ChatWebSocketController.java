package com.community.controller;

import com.community.dto.ChatRoomMessageRequest;
import com.community.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    /**
     * 채팅방 메시지 전송 (WebSocket)
     */
    @MessageMapping("/chat.send")
    public void sendRoomMessage(@Payload ChatRoomMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        // 1. Payload에서 먼저 확인 (가장 확실함)
        Long userId = request.getUserId();
        
        // 2. 없으면 세션에서 확인 (Fallback)
        if (userId == null) {
            String sessionUserId = (String) headerAccessor.getSessionAttributes().get("userId");
            if (sessionUserId != null) {
                userId = Long.parseLong(sessionUserId);
            }
        }

        if (userId == null) {
            System.err.println("❌ WebSocket Message Failed: User ID is missing in both Payload and Session.");
            return; // 또는 예외 처리
        }

        System.out.println("✅ WebSocket Message Received - Room: " + request.getRoomId() + ", User: " + userId + ", Content: " + request.getContent());

        try {
            // 메시지 저장 및 브로드캐스트
            chatService.sendMessage(request.getRoomId(), userId, request.getContent());
        } catch (Exception e) {
            System.err.println("❌ Failed to process message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 타이핑 인디케이터 처리
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload Map<String, Object> payload) {
        Long roomId = Long.valueOf(payload.get("roomId").toString());
        Long userId = Long.valueOf(payload.get("userId").toString());
        Boolean isTyping = Boolean.valueOf(payload.get("isTyping").toString());

        // 같은 방의 다른 사용자들에게 브로드캐스트
        messagingTemplate.convertAndSend(
                "/topic/chat/room/" + roomId,
                Map.of(
                        "type", "TYPING",
                        "data", Map.of(
                                "userId", userId,
                                "isTyping", isTyping
                        )
                )
        );
    }
}
