package com.community.config;

import com.community.dto.PlayerJoinDto;
import com.community.service.ActiveUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ActiveUserService activeUserService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        // HandshakeInterceptor에서 저장한 세션 속성 가져오기
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");
        String sessionId = headerAccessor.getSessionId();

        if (username != null && userId != null && sessionId != null) {
            log.info("Received a new web socket connection from user: {} ({})", username, userId);

            // ActiveUserService에 등록 (username 포함)
            activeUserService.addUser(userId, sessionId, username);
            
            // 접속 알림 브로드캐스트
            PlayerJoinDto joinDto = new PlayerJoinDto();
            joinDto.setUserId(userId);
            joinDto.setUsername(username);
            joinDto.setAction("join");
            joinDto.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/players", joinDto);
             
            // 온라인 인원 수 업데이트
            messagingTemplate.convertAndSend("/topic/online-count",
                    activeUserService.getActiveUserCount());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");

        if (username != null && userId != null) {
            log.info("User Disconnected : " + username);

            // ActiveUserService에서 사용자 제거
            String sessionId = headerAccessor.getSessionId();
            if (sessionId != null) {
                activeUserService.removeUserBySession(sessionId);
                log.info("Removed user {} from active users. Current count: {}",
                        userId, activeUserService.getActiveUserCount());
            }

            // 다른 플레이어들에게 퇴장 알림
            PlayerJoinDto leaveDto = new PlayerJoinDto();
            leaveDto.setUserId(userId);
            leaveDto.setUsername(username);
            leaveDto.setAction("leave");
            leaveDto.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/players", leaveDto);

            // 온라인 인원 수 업데이트 브로드캐스트
            messagingTemplate.convertAndSend("/topic/online-count",
                    activeUserService.getActiveUserCount());
        }
    }
}
