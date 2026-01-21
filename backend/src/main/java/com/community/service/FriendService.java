package com.community.service;

import com.community.dto.FriendRequestDto;
import com.community.model.Friendship;
import com.community.model.Friendship.FriendshipStatus;
import com.community.model.User;
import com.community.repository.FriendshipRepository;
import com.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final ActiveUserService activeUserService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 친구 요청 보내기 (닉네임으로)
     */
    @Transactional
    public Friendship sendFriendRequest(Long requesterId, String targetUsername) {
        // 대상 사용자 찾기
        User addressee = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + targetUsername));

        // 본인에게 친구 요청 불가
        if (requesterId.equals(addressee.getId())) {
            throw new RuntimeException("자기 자신에게 친구 요청을 보낼 수 없습니다.");
        }

        // 이미 친구 관계가 있는지 확인
        Optional<Friendship> existing = friendshipRepository.findByUserIds(requesterId, addressee.getId());
        if (existing.isPresent()) {
            FriendshipStatus status = existing.get().getStatus();
            if (status == FriendshipStatus.ACCEPTED) {
                throw new RuntimeException("이미 친구입니다.");
            } else if (status == FriendshipStatus.PENDING) {
                throw new RuntimeException("이미 친구 요청을 보냈거나 받았습니다.");
            } else if (status == FriendshipStatus.REJECTED) {
                // 거절된 관계는 삭제 후 새로 생성
                friendshipRepository.delete(existing.get());
            }
        }

        // 친구 요청 생성
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("요청자를 찾을 수 없습니다."));

        Friendship friendship = Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendshipStatus.PENDING)
                .build();

        friendship = friendshipRepository.save(friendship);

        // WebSocket 알림: 받는 사람에게 친구 요청 알림
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "FRIEND_REQUEST");
        notification.put("friendshipId", friendship.getId());
        notification.put("requesterId", requester.getId());
        notification.put("requesterUsername", requester.getNickname());
        notification.put("requesterProfile", requester.getSelectedProfile() != null ? requester.getSelectedProfile().getId().intValue() : null);
        messagingTemplate.convertAndSend("/topic/friend-updates/" + addressee.getId(), notification);

        return friendship;
    }

    /**
     * 받은 친구 요청 목록
     */
    public List<FriendRequestDto> getReceivedRequests(Long userId) {
        List<Friendship> requests = friendshipRepository.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING);
        return requests.stream()
                .map(f -> {
                    boolean isOnline = activeUserService.isUserActive(f.getRequester().getId().toString());
                    return FriendRequestDto.fromFriendship(f, userId, isOnline);
                })
                .collect(Collectors.toList());
    }

    /**
     * 보낸 친구 요청 목록
     */
    public List<FriendRequestDto> getSentRequests(Long userId) {
        List<Friendship> requests = friendshipRepository.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING);
        return requests.stream()
                .map(f -> {
                    boolean isOnline = activeUserService.isUserActive(f.getAddressee().getId().toString());
                    return FriendRequestDto.fromFriendship(f, userId, isOnline);
                })
                .collect(Collectors.toList());
    }

    /**
     * 친구 요청 수락
     */
    @Transactional
    public void acceptFriendRequest(Long userId, Long friendshipId) {
        Friendship friendship = friendshipRepository.findByIdWithUsers(friendshipId)
                .orElseThrow(() -> new RuntimeException("친구 요청을 찾을 수 없습니다."));

        // 요청 받은 사람만 수락 가능
        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new RuntimeException("친구 요청을 수락할 권한이 없습니다.");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("이미 처리된 요청입니다.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);

        // WebSocket 알림: 요청자에게 수락 알림
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "FRIEND_ACCEPTED");
        notification.put("friendshipId", friendship.getId());
        notification.put("acceptorId", friendship.getAddressee().getId());
        notification.put("acceptorUsername", friendship.getAddressee().getNickname());
        messagingTemplate.convertAndSend("/topic/friend-updates/" + friendship.getRequester().getId(), notification);
    }

    /**
     * 친구 요청 거절
     */
    @Transactional
    public void rejectFriendRequest(Long userId, Long friendshipId) {
        Friendship friendship = friendshipRepository.findByIdWithUsers(friendshipId)
                .orElseThrow(() -> new RuntimeException("친구 요청을 찾을 수 없습니다."));

        // 요청 받은 사람만 거절 가능
        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new RuntimeException("친구 요청을 거절할 권한이 없습니다.");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("이미 처리된 요청입니다.");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }

    /**
     * 친구 목록 조회
     */
    public List<FriendRequestDto> getFriends(Long userId) {
        List<Friendship> friendships = friendshipRepository.findFriendsByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED);
        return friendships.stream()
                .map(f -> {
                    // 친구의 온라인 여부 확인
                    User friend = f.getRequester().getId().equals(userId) ? f.getAddressee() : f.getRequester();
                    boolean isOnline = activeUserService.isUserActive(friend.getId().toString());
                    return FriendRequestDto.fromFriendship(f, userId, isOnline);
                })
                .collect(Collectors.toList());
    }

    /**
     * 친구 삭제
     */
    @Transactional
    public void removeFriend(Long userId, Long friendshipId) {
        Friendship friendship = friendshipRepository.findByIdWithUsers(friendshipId)
                .orElseThrow(() -> new RuntimeException("친구 관계를 찾을 수 없습니다."));

        // 해당 친구 관계의 당사자만 삭제 가능
        if (!friendship.getRequester().getId().equals(userId) && !friendship.getAddressee().getId().equals(userId)) {
            throw new RuntimeException("친구를 삭제할 권한이 없습니다.");
        }

        friendshipRepository.delete(friendship);
    }

    /**
     * 사용자 검색 (닉네임으로)
     */
    public FriendRequestDto searchUserByUsername(String username, Long currentUserId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        if (user.getId().equals(currentUserId)) {
            throw new RuntimeException("자기 자신은 검색할 수 없습니다.");
        }

        boolean isOnline = activeUserService.isUserActive(user.getId().toString());
        return FriendRequestDto.fromUser(user, isOnline);
    }
}
