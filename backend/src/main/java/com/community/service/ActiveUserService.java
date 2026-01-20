package com.community.service;

import com.community.dto.ActiveSessionDto;
import com.community.model.User;
import com.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 활성 사용자 세션 관리 서비스
 * WebSocket 연결 중인 사용자들을 추적하여 중복 로그인 방지 및 온라인 인원 수 관리
 */
@Service
@RequiredArgsConstructor
public class ActiveUserService {

    private final UserRepository userRepository;

    // userId -> sessionId 매핑
    private final Map<String, String> activeUsers = new ConcurrentHashMap<>();

    // sessionId -> userId 매핑 (역참조용)
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    // sessionId -> SessionMetadata 매핑 (세션 상세 정보)
    private final Map<String, SessionMetadata> sessionMetadata = new ConcurrentHashMap<>();

    /**
     * 세션 메타데이터 내부 클래스
     */
    private static class SessionMetadata {
        String username;
        LocalDateTime connectedAt;

        SessionMetadata(String username, LocalDateTime connectedAt) {
            this.username = username;
            this.connectedAt = connectedAt;
        }
    }

    /**
     * 사용자가 이미 접속 중인지 확인
     * @param userId 사용자 ID
     * @return 접속 중이면 true, 아니면 false
     */
    public boolean isUserActive(String userId) {
        return activeUsers.containsKey(userId);
    }

    /**
     * 사용자 세션 등록 (접속)
     * @param userId 사용자 ID
     * @param sessionId WebSocket 세션 ID
     * @return 성공적으로 등록되면 true, 이미 접속 중이면 false
     */
    public boolean addUser(String userId, String sessionId) {
        return addUser(userId, sessionId, null);
    }

    /**
     * 사용자 세션 등록 (접속) - username 포함
     * @param userId 사용자 ID
     * @param sessionId WebSocket 세션 ID
     * @param username 사용자명
     * @return 성공적으로 등록되면 true, 이미 접속 중이면 false
     */
    public boolean addUser(String userId, String sessionId, String username) {
        if (isUserActive(userId)) {
            return false; // 이미 접속 중
        }

        String finalUsername = username;
        if (finalUsername == null) {
            try {
                Long id = Long.parseLong(userId);
                finalUsername = userRepository.findById(id)
                        .map(User::getNickname)
                        .orElse("Unknown");
            } catch (Exception e) {
                finalUsername = "Unknown";
            }
        }

        activeUsers.put(userId, sessionId);
        sessionToUser.put(sessionId, userId);
        sessionMetadata.put(sessionId, new SessionMetadata(finalUsername, LocalDateTime.now()));
        return true;
    }

    /**
     * 사용자 세션 제거 (접속 종료)
     * @param sessionId WebSocket 세션 ID
     * @return 제거된 사용자 ID (없으면 null)
     */
    public String removeUserBySession(String sessionId) {
        String userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            activeUsers.remove(userId);
            sessionMetadata.remove(sessionId);
        }
        return userId;
    }

    /**
     * 사용자 ID로 세션 제거
     * @param userId 사용자 ID
     * @return 제거된 세션 ID (없으면 null)
     */
    public String removeUserById(String userId) {
        String sessionId = activeUsers.remove(userId);
        if (sessionId != null) {
            sessionToUser.remove(sessionId);
            sessionMetadata.remove(sessionId);
        }
        return sessionId;
    }

    /**
     * 세션 ID로 사용자 ID 조회
     * @param sessionId WebSocket 세션 ID
     * @return 사용자 ID (없으면 null)
     */
    public String getUserIdBySession(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    /**
     * 사용자 ID로 세션 ID 조회
     * @param userId 사용자 ID
     * @return 세션 ID (없으면 null)
     */
    public String getSessionIdByUserId(String userId) {
        return activeUsers.get(userId);
    }

    /**
     * 현재 접속 중인 사용자 수
     * @return 온라인 인원 수
     */
    public int getActiveUserCount() {
        return activeUsers.size();
    }

    /**
     * 모든 활성 사용자 정보 조회 (디버깅용)
     * @return 활성 사용자 맵
     */
    public Map<String, String> getAllActiveUsers() {
        return new ConcurrentHashMap<>(activeUsers);
    }

    /**
     * 모든 활성 세션 상세 정보 조회 (관리자용)
     * @return 활성 세션 DTO 리스트
     */
    public List<ActiveSessionDto> getAllActiveSessions() {
        List<ActiveSessionDto> sessions = new ArrayList<>();
        for (Map.Entry<String, String> entry : activeUsers.entrySet()) {
            String userId = entry.getKey();
            String sessionId = entry.getValue();
            SessionMetadata metadata = sessionMetadata.get(sessionId);

            sessions.add(ActiveSessionDto.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .username(metadata != null ? metadata.username : "Unknown")
                    .connectedAt(metadata != null ? metadata.connectedAt : null)
                    .build());
        }
        return sessions;
    }
}
