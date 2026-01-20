package com.community.controller;

import com.community.dto.ChatMessageDto;
import com.community.dto.ChatRoomDto;
import com.community.model.ChatRoom;
import com.community.model.User;
import com.community.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 대화방 목록 조회
     */
    @GetMapping("/rooms")
    public ResponseEntity<?> getRooms(@AuthenticationPrincipal User currentUser) {
        try {
            List<ChatRoomDto> rooms = chatService.getUserRooms(currentUser.getId());
            return ResponseEntity.ok(rooms);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "대화방 목록 조회 실패: " + e.getMessage())
            );
        }
    }

    /**
     * 대화방 생성
     */
    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, Object> request
    ) {
        try {
            if (!request.containsKey("type") || request.get("type") == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "type is required and cannot be null"));
            }
            if (!request.containsKey("inviteUserIds") || request.get("inviteUserIds") == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "inviteUserIds is required and cannot be null"));
            }

            String typeStr = request.get("type").toString();
            ChatRoom.RoomType type = ChatRoom.RoomType.valueOf(typeStr);
            
            // 안전하게 Long 리스트로 변환
            List<?> rawIds = (List<?>) request.get("inviteUserIds");
            List<Long> inviteUserIds = rawIds.stream()
                    .filter(id -> id != null) // null 요소 필터링
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toList());

            Object titleObj = request.get("title");
            String title = titleObj != null ? titleObj.toString() : null;

            ChatRoomDto room = chatService.createRoom(currentUser.getId(), type, inviteUserIds, title);
            return ResponseEntity.ok(room);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Invalid RoomType: " + e.getMessage())
            );
        } catch (Exception e) {
            e.printStackTrace(); // 서버 로그에 출력
            return ResponseEntity.badRequest().body(
                    Map.of("message", "대화방 생성 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage())
            );
        }
    }

    /**
     * 메시지 내역 조회
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "50") int size
    ) {
        try {
            List<ChatMessageDto> messages = chatService.getRoomMessages(
                    roomId, currentUser.getId(), lastId, size
            );
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "메시지 조회 실패: " + e.getMessage())
            );
        }
    }

    /**
     * 메시지 전송
     */
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long roomId,
            @RequestBody Map<String, String> request
    ) {
        try {
            String content = request.get("content");
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "메시지 내용이 비어있습니다.")
                );
            }

            if (content.length() > 5000) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "메시지가 너무 깁니다. (최대 5000자)")
                );
            }

            ChatMessageDto message = chatService.sendMessage(
                    roomId, currentUser.getId(), content
            );
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "메시지 전송 실패: " + e.getMessage())
            );
        }
    }

    /**
     * 읽음 처리
     */
    @PatchMapping("/rooms/{roomId}/read")
    public ResponseEntity<?> markAsRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long roomId,
            @RequestBody Map<String, Long> request
    ) {
        try {
            Long lastMessageId = request.get("lastMessageId");
            chatService.markAsRead(roomId, currentUser.getId(), lastMessageId);
            return ResponseEntity.ok(Map.of("message", "읽음 처리 완료"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "읽음 처리 실패: " + e.getMessage())
            );
        }
    }

    /**
     * 사용자 초대
     */
    @PostMapping("/rooms/{roomId}/invite")
    public ResponseEntity<?> inviteUsers(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            if (!request.containsKey("userIds") || request.get("userIds") == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "userIds is required and cannot be null"));
            }

            // 안전하게 Long 리스트로 변환
            List<?> rawIds = (List<?>) request.get("userIds");
            List<Long> userIds = rawIds.stream()
                    .filter(id -> id != null)
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toList());

            chatService.inviteUsers(roomId, currentUser.getId(), userIds);
            return ResponseEntity.ok(Map.of("message", "사용자 초대 완료"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    Map.of("message", "사용자 초대 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage())
            );
        }
    }

    /**
     * 방 나가기
     */
    @DeleteMapping("/rooms/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long roomId
    ) {
        try {
            chatService.leaveRoom(roomId, currentUser.getId());
            return ResponseEntity.ok(Map.of("message", "방 나가기 완료"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "방 나가기 실패: " + e.getMessage())
            );
        }
    }

    /**
     * 방 제목 수정
     */
    @PatchMapping("/rooms/{roomId}")
    public ResponseEntity<?> updateRoomTitle(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long roomId,
            @RequestBody Map<String, String> request
    ) {
        try {
            String title = request.get("title");
            chatService.updateRoomTitle(roomId, currentUser.getId(), title);
            return ResponseEntity.ok(Map.of("message", "방 제목 수정 완료"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "방 제목 수정 실패: " + e.getMessage())
            );
        }
    }
}
