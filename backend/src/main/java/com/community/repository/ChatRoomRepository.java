package com.community.repository;

import com.community.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT DISTINCT cr FROM ChatRoom cr " +
           "JOIN cr.participants p " +
           "WHERE p.user.id = :userId " +
           "ORDER BY cr.updatedAt DESC")
    List<ChatRoom> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT DISTINCT cr FROM ChatRoom cr " +
           "JOIN FETCH cr.participants p " +
           "JOIN FETCH p.user u " +
           "WHERE cr.id IN (SELECT p2.room.id FROM ChatParticipant p2 WHERE p2.user.id = :userId) " +
           "ORDER BY cr.updatedAt DESC")
    List<ChatRoom> findByUserIdWithParticipants(@Param("userId") Long userId);

    @Query("SELECT cr FROM ChatRoom cr " +
           "JOIN cr.participants p1 " +
           "JOIN cr.participants p2 " +
           "WHERE cr.type = 'DM' " +
           "AND p1.user.id = :userId1 " +
           "AND p2.user.id = :userId2")
    Optional<ChatRoom> findDMRoomBetweenUsers(
        @Param("userId1") Long userId1,
        @Param("userId2") Long userId2
    );
}
