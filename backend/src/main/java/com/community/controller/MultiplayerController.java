package com.community.controller;

import com.community.dto.ChatMessageDto;
import com.community.dto.PlayerJoinDto;
import com.community.dto.PlayerPositionDto;
import com.community.service.ActiveUserService;
import com.community.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MultiplayerController {

    private final ActiveUserService activeUserService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final MessageService messageService;

    /**
     * 플레이어 입장
     * Client -> /app/player.join
     * Server -> /topic/players (broadcast to all)
     */
    @MessageMapping("/player.join")
    @SendTo("/topic/players")
    public PlayerJoinDto playerJoin(PlayerJoinDto joinDto, SimpMessageHeaderAccessor headerAccessor) {
        String userId = joinDto.getUserId();
        String sessionId = headerAccessor.getSessionId();

        // 중복 로그인 체크
        if (activeUserService.isUserActive(userId)) {
            log.warn("Duplicate login attempt for userId: {}", userId);

            // 중복 로그인 시도를 특별한 action으로 브로드캐스트
            PlayerJoinDto errorDto = new PlayerJoinDto();
            errorDto.setUserId(userId);
            errorDto.setUsername(joinDto.getUsername());
            errorDto.setAction("duplicate");
            errorDto.setTimestamp(System.currentTimeMillis());

            return errorDto; // 중복 로그인 알림 브로드캐스트
        }

        // 사용자 등록
        boolean added = activeUserService.addUser(userId, sessionId, joinDto.getUsername());
        if (!added) {
            log.error("Failed to add user {} to active users", userId);
            return null;
        }

        // Add username in websocket session
        headerAccessor.getSessionAttributes().put("username", joinDto.getUsername());
        headerAccessor.getSessionAttributes().put("userId", userId);

        joinDto.setAction("join");
        joinDto.setTimestamp(System.currentTimeMillis());

        log.info("User {} joined. Current online count: {}", userId, activeUserService.getActiveUserCount());

        // 온라인 인원 수 브로드캐스트
        messagingTemplate.convertAndSend("/topic/online-count",
                activeUserService.getActiveUserCount());

        return joinDto;
    }

    /**
     * 플레이어 위치 업데이트
     * Client -> /app/player.position
     * Server -> /topic/positions (broadcast to all)
     */
    @MessageMapping("/player.position")
    @SendTo("/topic/positions")
    public PlayerPositionDto updatePosition(PlayerPositionDto positionDto) {
        positionDto.setTimestamp(System.currentTimeMillis());
        return positionDto;
    }

    /**
     * 전체 채팅 메시지
     * Client -> /app/chat.message
     * Server -> /topic/chat (broadcast to all)
     */
    @MessageMapping("/chat.message")
    @SendTo("/topic/chat")
    public ChatMessageDto sendChatMessage(ChatMessageDto chatDto) {
        chatDto.setTimestamp(System.currentTimeMillis());

        // 광장 메시지를 데이터베이스에 저장
        try {
            Long senderId = Long.parseLong(chatDto.getUserId());
            messageService.savePlazaMessage(senderId, chatDto.getMessage());
            log.info("광장 메시지 저장 완료: userId={}, message={}", senderId, chatDto.getMessage());
        } catch (Exception e) {
            log.error("광장 메시지 저장 실패: {}", e.getMessage(), e);
        }

        return chatDto;
    }
}
