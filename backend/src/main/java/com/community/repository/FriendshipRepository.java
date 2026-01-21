package com.community.repository;

import com.community.model.Friendship;
import com.community.model.Friendship.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // 특정 사용자 간 친구 관계 조회
    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :userId1 AND f.addressee.id = :userId2) OR (f.requester.id = :userId2 AND f.addressee.id = :userId1)")
    Optional<Friendship> findByUserIds(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // 수락된 친구 목록 조회 (JOIN FETCH로 User 정보 미리 로드)
    @Query("SELECT f FROM Friendship f " +
           "JOIN FETCH f.requester " +
           "JOIN FETCH f.addressee " +
           "WHERE (f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = :status")
    List<Friendship> findFriendsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") FriendshipStatus status);

    // 받은 친구 요청 조회 (JOIN FETCH로 requester 정보 미리 로드)
    @Query("SELECT f FROM Friendship f " +
           "JOIN FETCH f.requester " +
           "JOIN FETCH f.addressee " +
           "WHERE f.addressee.id = :addresseeId AND f.status = :status")
    List<Friendship> findByAddresseeIdAndStatus(@Param("addresseeId") Long addresseeId, @Param("status") FriendshipStatus status);

    // 보낸 친구 요청 조회 (JOIN FETCH로 addressee 정보 미리 로드)
    @Query("SELECT f FROM Friendship f " +
           "JOIN FETCH f.requester " +
           "JOIN FETCH f.addressee " +
           "WHERE f.requester.id = :requesterId AND f.status = :status")
    List<Friendship> findByRequesterIdAndStatus(@Param("requesterId") Long requesterId, @Param("status") FriendshipStatus status);

    // ID로 조회 (JOIN FETCH로 User 정보 미리 로드)
    @Query("SELECT f FROM Friendship f " +
           "JOIN FETCH f.requester " +
           "JOIN FETCH f.addressee " +
           "WHERE f.id = :id")
    Optional<Friendship> findByIdWithUsers(@Param("id") Long id);

    // 친구 관계 존재 여부 확인
    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE ((f.requester.id = :userId1 AND f.addressee.id = :userId2) OR (f.requester.id = :userId2 AND f.addressee.id = :userId1)) AND f.status = :status")
    boolean existsByUserIdsAndStatus(@Param("userId1") Long userId1, @Param("userId2") Long userId2, @Param("status") FriendshipStatus status);
}
