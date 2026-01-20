package com.community.repository;

import com.community.model.Message;
import com.community.model.Message.MessageType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // 광장 채팅 조회 (최근순)
    List<Message> findByMessageTypeAndIsDeletedOrderByCreatedAtDesc(MessageType messageType, Boolean isDeleted, Pageable pageable);

    // 로컬 방 채팅 조회 (최근순)
    List<Message> findByMessageTypeAndRoomIdAndIsDeletedOrderByCreatedAtDesc(MessageType messageType, Long roomId, Boolean isDeleted, Pageable pageable);

    // DM 조회 (두 사용자 간 대화, 최근순)
    @Query("SELECT m FROM Message m WHERE m.messageType = 'DM' AND m.isDeleted = false AND ((m.sender.id = :userId1 AND m.receiver.id = :userId2) OR (m.sender.id = :userId2 AND m.receiver.id = :userId1)) ORDER BY m.createdAt DESC")
    List<Message> findDMBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2, Pageable pageable);

    // 채팅방 메시지 조회 (최근순 + 페이징)
    List<Message> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    // 특정 시간 이후 메시지 조회 (실시간 업데이트용)
    @Query("SELECT m FROM Message m WHERE m.messageType = :messageType AND m.roomId = :roomId AND m.createdAt > :since AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<Message> findRecentMessagesInRoom(@Param("messageType") MessageType messageType, @Param("roomId") Long roomId, @Param("since") LocalDateTime since);

    // 광장 최근 메시지 조회
    @Query("SELECT m FROM Message m WHERE m.messageType = 'PLAZA' AND m.createdAt > :since AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<Message> findRecentPlazaMessages(@Param("since") LocalDateTime since);

    // 사용자의 읽지 않은 DM 개수 (deprecated - use countUnreadDMsFromFriend)
    @Query("SELECT COUNT(m) FROM Message m WHERE m.messageType = 'DM' AND m.receiver.id = :userId AND m.createdAt > :lastCheckTime AND m.isDeleted = false")
    Long countUnreadDMs(@Param("userId") Long userId, @Param("lastCheckTime") LocalDateTime lastCheckTime);

    // 특정 친구로부터 받은 읽지 않은 DM 개수
    @Query("SELECT COUNT(m) FROM Message m WHERE m.messageType = 'DM' AND m.receiver.id = :receiverId AND m.sender.id = :senderId AND m.isRead = false AND m.isDeleted = false")
    Long countUnreadDMsFromFriend(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);

    // 특정 친구와의 대화에서 읽지 않은 메시지를 읽음 처리
    @Query("UPDATE Message m SET m.isRead = true, m.readAt = :readAt WHERE m.messageType = 'DM' AND m.receiver.id = :receiverId AND m.sender.id = :senderId AND m.isRead = false AND m.isDeleted = false")
    @org.springframework.data.jpa.repository.Modifying
    void markMessagesAsRead(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId, @Param("readAt") LocalDateTime readAt);

    // 관리자: 7일 이상 된 메시지 삭제
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);

    // 관리자: 특정 사용자의 메시지 검색 (페이지네이션)
    @Query("SELECT m FROM Message m WHERE m.sender.id = :userId ORDER BY m.createdAt DESC")
    org.springframework.data.domain.Page<Message> findBySenderId(@Param("userId") Long userId, Pageable pageable);

    // 관리자: 키워드로 메시지 검색 (페이지네이션)
    @Query("SELECT m FROM Message m WHERE m.content LIKE %:keyword% ORDER BY m.createdAt DESC")
    org.springframework.data.domain.Page<Message> findByContentContaining(@Param("keyword") String keyword, Pageable pageable);

    // 관리자: 메시지 타입별 검색 (페이지네이션)
    org.springframework.data.domain.Page<Message> findByMessageTypeOrderByCreatedAtDesc(MessageType messageType, Pageable pageable);

    // 관리자: 전체 메시지 조회 (페이지네이션)
    org.springframework.data.domain.Page<Message> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
