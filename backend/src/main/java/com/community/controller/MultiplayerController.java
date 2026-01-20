package com.community.controller;

import com.community.dto.ChatMessageDto;
import com.community.dto.PlayerJoinDto;
import com.community.dto.PlayerPositionDto;
import com.community.dto.RoomDto;
import com.community.dto.MinigameChatDto;
import com.community.service.ActiveUserService;
import com.community.service.MessageService;
import com.community.service.PersonalRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MultiplayerController {

    private final ActiveUserService activeUserService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final MessageService messageService;
    private final PersonalRoomService personalRoomService;

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
        
        // 개인방 목록 업데이트 브로드캐스트 (새 호스트가 접속하면 방이 목록에 표시됨)
        List<RoomDto> updatedRooms = personalRoomService.getAllRooms();
        messagingTemplate.convertAndSend("/topic/rooms/list", updatedRooms);
        log.info("방 목록 업데이트 브로드캐스트 (사용자 접속): {} rooms", updatedRooms.size());

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

    /**
     * 개인 방 생성
     * Client -> /app/room.create
     * Server -> /topic/rooms (broadcast to all)
     */
    @MessageMapping("/room.create")
    @SendTo("/topic/rooms")
    public RoomDto createRoom(RoomDto roomDto) {
        // PersonalRoomService에 방 저장
        RoomDto savedRoom = personalRoomService.createRoom(roomDto);
        if (savedRoom == null) {
            log.warn("방 생성 실패: roomId={}", roomDto.getRoomId());
            return null;
        }
        
        log.info("방 생성 브로드캐스트: roomId={}, roomName={}, hostName={}, 현재 방 개수={}", 
                savedRoom.getRoomId(), savedRoom.getRoomName(), savedRoom.getHostName(),
                personalRoomService.getRoomCount());
        return savedRoom;
    }

    /**
     * 개인 방 삭제
     * Client -> /app/room.delete
     * Server -> /topic/rooms (broadcast to all)
     */
    @MessageMapping("/room.delete")
    @SendTo("/topic/rooms")
    public RoomDto deleteRoom(RoomDto roomDto) {
        // PersonalRoomService에서 방 삭제
        RoomDto deletedRoom = personalRoomService.deleteRoom(roomDto.getRoomId());
        if (deletedRoom == null) {
            // 방이 없어도 삭제 브로드캐스트는 전송 (클라이언트 동기화용)
            roomDto.setAction("delete");
            roomDto.setTimestamp(System.currentTimeMillis());
            log.warn("삭제할 방 없음, 브로드캐스트만 전송: roomId={}", roomDto.getRoomId());
            return roomDto;
        }
        
        log.info("방 삭제 브로드캐스트: roomId={}, 남은 방 개수={}", 
                deletedRoom.getRoomId(), personalRoomService.getRoomCount());
        return deletedRoom;
    }

    /**
     * 활성 방 목록 요청
     * Client -> /app/room.list
     * Server -> /user/queue/rooms (개인 메시지로 응답)
     */
    @MessageMapping("/room.list")
    public void getRoomList(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        List<RoomDto> rooms = personalRoomService.getAllRooms();
        
        log.info("방 목록 요청: sessionId={}, 방 개수={}", sessionId, rooms.size());
        
        // 요청한 클라이언트에게만 방 목록 전송
        messagingTemplate.convertAndSendToUser(
            sessionId, 
            "/queue/rooms", 
            rooms,
            createHeaders(sessionId)
        );
    }
    
    private org.springframework.messaging.MessageHeaders createHeaders(String sessionId) {
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor = 
            org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }

    /**
     * 개인 룸 채팅
     * Client -> /app/room.chat
     * Server -> /topic/room/{roomId}/chat (to room)
     */
    @MessageMapping("/room.chat")
    public void sendRoomChat(MinigameChatDto chatDto) {
        if (chatDto == null || chatDto.getRoomId() == null) return;
        chatDto.setTimestamp(System.currentTimeMillis());
        log.info("개인 룸 채팅: roomId={}, userId={}, message={}", chatDto.getRoomId(), chatDto.getUserId(), chatDto.getMessage());
        messagingTemplate.convertAndSend("/topic/room/" + chatDto.getRoomId() + "/chat", chatDto);
    }
}
