package com.community.config;

import com.community.dto.PlayerJoinDto;
import com.community.dto.RoomDto;
import com.community.service.ActiveUserService;
import com.community.service.PersonalRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ActiveUserService activeUserService;
    private final PersonalRoomService personalRoomService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // 세션 속성이 null일 수 있음
        var sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.debug("Session attributes is null, skipping connect event");
            return;
        }

        // HandshakeInterceptor에서 저장한 세션 속성 가져오기
        String username = (String) sessionAttributes.get("username");
        String userId = (String) sessionAttributes.get("userId");
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

        // 세션 속성이 null일 수 있음
        var sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.debug("Session attributes is null, skipping disconnect event");
            return;
        }

        String username = (String) sessionAttributes.get("username");
        String userId = (String) sessionAttributes.get("userId");

        if (username != null && userId != null) {
            log.info("User Disconnected : " + username);

            // ActiveUserService에서 사용자 제거
            String sessionId = headerAccessor.getSessionId();
            if (sessionId != null) {
                activeUserService.removeUserBySession(sessionId);
                log.info("Removed user {} from active users. Current count: {}",
                        userId, activeUserService.getActiveUserCount());
            }

            // 개인 룸은 호스트가 나가도 삭제하지 않음 (명시적인 삭제 요청 시에만 삭제)
            // 방은 DB에 영구 저장되어 호스트가 다시 접속하면 기존 방을 사용할 수 있음
            log.info("User {} disconnected but personal room preserved (if any)", userId);

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
            
            // 개인방 목록 업데이트 브로드캐스트 (오프라인된 호스트의 방 제거)
            List<RoomDto> updatedRooms = personalRoomService.getAllRooms();
            messagingTemplate.convertAndSend("/topic/rooms/list", updatedRooms);
            log.info("방 목록 업데이트 브로드캐스트 (사용자 해제): {} rooms", updatedRooms.size());
        }
    }
}
