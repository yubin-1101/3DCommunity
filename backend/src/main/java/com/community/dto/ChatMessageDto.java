package com.community.dto;

import com.community.model.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    // 기존 필드 (하위 호환성)
    private String userId;
    private String username;
    private String message;
    private Long timestamp;
    
    // 새로운 필드
    private Long id;
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;
    private Boolean isRead;

    public static ChatMessageDto fromEntity(Message msg) {
        return ChatMessageDto.builder()
                .id(msg.getId())
                .senderId(msg.getSender().getId())
                .senderName(msg.getSender().getUsername())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .isRead(msg.getIsRead())
                // 하위 호환성
                .userId(msg.getSender().getId().toString())
                .username(msg.getSender().getUsername())
                .message(msg.getContent())
                .timestamp(msg.getCreatedAt() != null ? 
                    java.sql.Timestamp.valueOf(msg.getCreatedAt()).getTime() : null)
                .build();
    }
}