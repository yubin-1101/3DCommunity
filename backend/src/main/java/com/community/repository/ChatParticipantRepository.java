package com.community.repository;

import com.community.model.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    List<ChatParticipant> findByRoomId(Long roomId);

    Optional<ChatParticipant> findByRoomIdAndUserId(Long roomId, Long userId);

    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.chatRoom.id = :roomId " +
           "AND m.id > :lastReadMessageId " +
           "AND m.sender.id != :userId")
    Long countUnreadMessages(
        @Param("roomId") Long roomId,
        @Param("userId") Long userId,
        @Param("lastReadMessageId") Long lastReadMessageId
    );

    @Query("SELECT m.chatRoom.id, COUNT(m) FROM Message m " +
           "JOIN ChatParticipant cp ON cp.room.id = m.chatRoom.id AND cp.user.id = :userId " +
           "WHERE m.chatRoom.id IN :roomIds " +
           "AND m.sender.id != :userId " +
           "AND m.id > COALESCE(cp.lastReadMessageId, 0) " +
           "GROUP BY m.chatRoom.id")
    List<Object[]> countUnreadMessagesForRooms(
        @Param("roomIds") List<Long> roomIds,
        @Param("userId") Long userId
    );
}
