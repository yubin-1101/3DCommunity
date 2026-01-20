package com.community.dto;

import com.community.model.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDto {
    private Long id;
    private String type;
    private String title;
    private String lastMessage;
    private LocalDateTime updatedAt;
    private Integer unreadCount;

    // 메신저 앱 지원을 위한 필드
    private String profileImagePath;
    private String outlineImagePath;
    private boolean isOnline;

    public static ChatRoomDto fromEntity(ChatRoom room, Integer unreadCount) {
        return ChatRoomDto.builder()
                .id(room.getId())
                .type(room.getType().name())
                .title(room.getTitle())
                .lastMessage(room.getLastMessage())
                .updatedAt(room.getUpdatedAt())
                .unreadCount(unreadCount != null ? unreadCount : 0)
                .build();
    }
}
