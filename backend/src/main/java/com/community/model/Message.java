package com.community.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_message_sender", columnList = "sender_id"),
    @Index(name = "idx_message_receiver", columnList = "receiver_id"),
    @Index(name = "idx_message_type", columnList = "message_type"),
    @Index(name = "idx_message_created_at", columnList = "created_at"),
    @Index(name = "idx_message_is_deleted", columnList = "is_deleted"),
    @Index(name = "idx_message_is_read", columnList = "is_read"),
    @Index(name = "idx_message_chat_room", columnList = "chat_room_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(name = "room_id")
    private Long roomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // JSONB 타입은 PostgreSQL 전용이므로 String으로 저장 후 파싱
    @Column(columnDefinition = "TEXT")
    private String emoticon;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isDeleted == null) {
            isDeleted = false;
        }
        if (isRead == null) {
            isRead = false;
        }
    }

    public enum MessageType {
        PLAZA,
        LOCAL_ROOM,
        DM,
        CHAT_ROOM
    }
}
