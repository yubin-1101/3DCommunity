package com.community.service;

import com.community.dto.ChatMessageDto;
import com.community.dto.ChatRoomDto;
import com.community.model.*;
import com.community.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActiveUserService activeUserService;

    /**
     * 사용자의 대화방 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getUserRooms(Long userId) {
        // 1. 참여 중인 방과 참여자 정보를 한 번의 조인 쿼리로 가져옴 (N+1 방지)
        List<ChatRoom> rooms = chatRoomRepository.findByUserIdWithParticipants(userId);
        if (rooms.isEmpty()) return new ArrayList<>();

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).collect(Collectors.toList());

        // 2. 안 읽은 메시지 수를 한 번의 쿼리로 가져옴 (N+1 방지)
        List<Object[]> unreadCountsRaw = chatParticipantRepository.countUnreadMessagesForRooms(roomIds, userId);
        java.util.Map<Long, Long> unreadCountMap = unreadCountsRaw.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return rooms.stream()
                .map(room -> {
                    int unreadCount = unreadCountMap.getOrDefault(room.getId(), 0L).intValue();
                    ChatRoomDto dto = ChatRoomDto.fromEntity(room, unreadCount);
                    
                    // DM일 경우 상대방 정보 주입
                    if (room.getType() == ChatRoom.RoomType.DM) {
                        User otherUser = room.getParticipants().stream()
                            .filter(p -> !p.getUser().getId().equals(userId))
                            .map(ChatParticipant::getUser)
                            .findFirst()
                            .orElse(null);

                        if (otherUser != null) {
                            dto.setTitle(otherUser.getNickname());
                            dto.setProfileImagePath(otherUser.getSelectedProfile() != null ? 
                                    otherUser.getSelectedProfile().getImagePath() : "/resources/Profile/base-profile3.png");
                            dto.setOutlineImagePath(otherUser.getSelectedOutline() != null ? 
                                    otherUser.getSelectedOutline().getImagePath() : "/resources/ProfileOutline/base-outline1.png");
                            dto.setOnline(activeUserService.isUserActive(otherUser.getId().toString()));
                        }
                    } else {
                        // 그룹 방 기본 이미지/테두리 설정
                        dto.setProfileImagePath("/resources/Profile/base-profile3.png"); // 혹은 그룹 전용 이미지
                        dto.setOutlineImagePath("/resources/ProfileOutline/base-outline1.png");
                        dto.setOnline(false); // 그룹은 온라인 뱃지 미표시 또는 전체 인원 중 온라인 수 등 가능
                    }
                    
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 대화방 생성 (DM 또는 그룹)
     */
    @Transactional
    public ChatRoomDto createRoom(Long creatorId, ChatRoom.RoomType type, 
                                   List<Long> inviteUserIds, String title) {
        // DM의 경우 기존 방 확인
        if (type == ChatRoom.RoomType.DM && inviteUserIds.size() == 1) {
            var existingRoom = chatRoomRepository.findDMRoomBetweenUsers(
                    creatorId, inviteUserIds.get(0)
            );
            if (existingRoom.isPresent()) {
                // 기존 방 반환 시에도 상대방 이름으로 제목 설정
                ChatRoomDto dto = ChatRoomDto.fromEntity(existingRoom.get(), 0);
                User friend = userRepository.findById(inviteUserIds.get(0)).orElse(null);
                if (friend != null) dto.setTitle(friend.getNickname());
                return dto;
            }
        }

        // 새 방 생성
        ChatRoom room = ChatRoom.builder()
                .type(type)
                .title(title)
                .build();
        room = chatRoomRepository.save(room);

        // 참여자 추가
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        ChatParticipant creatorParticipant = ChatParticipant.builder()
                .room(room)
                .user(creator)
                .build();
        chatParticipantRepository.save(creatorParticipant);

        // 초대된 사용자 추가
        for (Long inviteUserId : inviteUserIds) {
            User invitedUser = userRepository.findById(inviteUserId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + inviteUserId));
            
            ChatParticipant participant = ChatParticipant.builder()
                    .room(room)
                    .user(invitedUser)
                    .build();
            chatParticipantRepository.save(participant);
        }

        ChatRoomDto dto = ChatRoomDto.fromEntity(room, 0);
        // DM일 경우 생성 직후 반환값에 상대방(초대된 사람) 이름 설정
        if (type == ChatRoom.RoomType.DM && inviteUserIds.size() == 1) {
             User friend = userRepository.findById(inviteUserIds.get(0)).orElse(null);
             if (friend != null) dto.setTitle(friend.getNickname());
        }
        return dto;
    }

    /**
     * 대화방 메시지 조회
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getRoomMessages(Long roomId, Long userId, Long lastId, int size) {
        // 권한 확인
        chatParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("Not a participant of this room"));

        // 메시지 조회 (페이징 적용)
        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtDesc(
            roomId, 
            org.springframework.data.domain.PageRequest.of(0, 50) // 최근 50개만
        );
        
        return messages.stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 메시지 전송
     */
    @Transactional
    public ChatMessageDto sendMessage(Long roomId, Long senderId, String content) {
        // 권한 확인
        chatParticipantRepository.findByRoomIdAndUserId(roomId, senderId)
                .orElseThrow(() -> new RuntimeException("Not a participant of this room"));

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 메시지 저장
        Message message = Message.builder()
                .chatRoom(room)
                .sender(sender)
                .content(content)
                .messageType(Message.MessageType.CHAT_ROOM)
                .build();
        message = messageRepository.save(message);

        // 방 정보 업데이트
        room.setLastMessage(content.length() > 50 ? content.substring(0, 50) + "..." : content);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        // WebSocket으로 브로드캐스트
        // WebSocket으로 브로드캐스트
        ChatMessageDto messageDto = ChatMessageDto.fromEntity(message);
        messagingTemplate.convertAndSend(
                "/topic/chat/room/" + roomId,
                java.util.Map.of(
                        "type", "MESSAGE",
                        "data", messageDto
                )
        );

        // [NEW] 참여자들에게 개별 알림 전송 (채팅방 밖에 있는 사용자를 위해)
        List<ChatParticipant> participants = chatParticipantRepository.findByRoomId(roomId);
        for (ChatParticipant participant : participants) {
            // 본인 제외 (선택 사항)
            if (participant.getUser().getId().equals(senderId)) continue;
            
            messagingTemplate.convertAndSend(
                    "/topic/user/" + participant.getUser().getId() + "/updates",
                    java.util.Map.of(
                            "type", "NEW_MESSAGE",
                            "chatRoomId", roomId,
                            "senderId", senderId,
                            "senderName", sender.getUsername(),
                            "content", content.length() > 50 ? content.substring(0, 50) + "..." : content
                    )
            );
        }

        return messageDto;
    }

    /**
     * 읽음 처리
     */
    @Transactional
    public void markAsRead(Long roomId, Long userId, Long lastMessageId) {
        ChatParticipant participant = chatParticipantRepository
                .findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("Not a participant"));

        // 더 최신 메시지일 때만 업데이트
        Long currentLastReadId = participant.getLastReadMessageId() != null ? participant.getLastReadMessageId() : 0L;
        if (lastMessageId != null && lastMessageId > currentLastReadId) {
            participant.setLastReadMessageId(lastMessageId);
            chatParticipantRepository.save(participant);

            // [NEW] 본인의 다른 기기/앱에 읽음 상태 알림
            messagingTemplate.convertAndSend(
                    "/topic/user/" + userId + "/updates",
                    java.util.Map.of(
                            "type", "READ_UPDATE",
                            "chatRoomId", roomId,
                            "lastReadMessageId", lastMessageId
                    )
            );
        }
    }

    /**
     * 사용자 초대
     */
    @Transactional
    public void inviteUsers(Long roomId, Long inviterId, List<Long> userIds) {
        // 권한 확인
        chatParticipantRepository.findByRoomIdAndUserId(roomId, inviterId)
                .orElseThrow(() -> new RuntimeException("Not a participant"));

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        for (Long userId : userIds) {
            // 이미 참여 중인지 확인
            if (chatParticipantRepository.findByRoomIdAndUserId(roomId, userId).isPresent()) {
                continue;
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            ChatParticipant participant = ChatParticipant.builder()
                    .room(room)
                    .user(user)
                    .build();
            chatParticipantRepository.save(participant);
        }
    }

    /**
     * 방 나가기
     */
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        ChatParticipant participant = chatParticipantRepository
                .findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("Not a participant"));

        chatParticipantRepository.delete(participant);
    }

    /**
     * 방 제목 수정
     */
    @Transactional
    public void updateRoomTitle(Long roomId, Long userId, String title) {
        // 권한 확인
        chatParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("Not a participant"));

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        room.setTitle(title);
        chatRoomRepository.save(room);
    }
}
