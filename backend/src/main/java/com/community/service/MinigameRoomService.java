package com.community.service;

import com.community.dto.MinigamePlayerDto;
import com.community.dto.MinigameRoomDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import com.community.dto.GameEventDto;
import com.community.dto.GameTargetDto;
import com.community.dto.GameScoreDto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MinigameRoomService {

    private final Map<String, MinigameRoomDto> rooms = new ConcurrentHashMap<>();

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    // Game sessions per room
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    // Reaction round state
    private final Map<String, Boolean> reactionActive = new ConcurrentHashMap<>();
    private final Map<String, String> reactionWinner = new ConcurrentHashMap<>();

    // Omok game state
    private final Map<String, OmokGameSession> omokSessions = new ConcurrentHashMap<>();

    /**
     * 방 생성
     */
    public MinigameRoomDto createRoom(String roomName, String gameName, String hostId, String hostName,
            int maxPlayers, boolean isLocked, int hostLevel,
            String selectedProfile, String selectedOutline, Double gpsLng, Double gpsLat) {
        String roomId = UUID.randomUUID().toString();

        MinigameRoomDto room = new MinigameRoomDto();
        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setGameName(gameName);
        room.setHostId(hostId);
        room.setHostName(hostName);
        room.setMaxPlayers(maxPlayers);
        room.setLocked(isLocked);
        room.setPlaying(false);
        room.setCurrentPlayers(1);
        room.setGpsLng(gpsLng);
        room.setGpsLat(gpsLat);

        MinigamePlayerDto host = new MinigamePlayerDto();
        host.setUserId(hostId);
        host.setUsername(hostName);
        host.setLevel(hostLevel);
        host.setHost(true);
        host.setReady(true);
        host.setSelectedProfile(selectedProfile);
        host.setSelectedOutline(selectedOutline);

        room.getPlayers().add(host);
        rooms.put(roomId, room);

        log.info("방 생성: {} (ID: {}, GPS: {}, {})", roomName, roomId, gpsLng, gpsLat);
        return room;
    }

    /**
     * 방 목록 조회
     */
    public List<MinigameRoomDto> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * 방 조회
     */
    public MinigameRoomDto getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 방 입장 (참가자 또는 관전자로)
     */
    public MinigameRoomDto joinRoom(String roomId, MinigamePlayerDto player) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            log.error("방을 찾을 수 없습니다: {}", roomId);
            return null;
        }

        // 이미 플레이어로 있는지 확인
        boolean alreadyPlayer = room.getPlayers().stream().anyMatch(p -> p.getUserId().equals(player.getUserId()));
        // 이미 관전자로 있는지 확인
        boolean alreadySpectator = room.getSpectators().stream().anyMatch(p -> p.getUserId().equals(player.getUserId()));

        if (alreadyPlayer || alreadySpectator) {
            log.info("플레이어 {}는 이미 방에 있습니다: {}", player.getUsername(), roomId);
            return room;
        }

        log.info("방 상태 before join - roomId: {}, currentPlayers: {}, maxPlayers: {}, players: {}",
                roomId, room.getCurrentPlayers(), room.getMaxPlayers(),
                room.getPlayers().stream().map(p -> p.getUserId()).toList());

        // 참가 인원이 가득 찬 경우 관전자로 입장
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            room.getSpectators().add(player);
            log.info("플레이어 {} 관전자로 입장: {} (관전자 수: {})",
                    player.getUsername(), roomId, room.getSpectators().size());
        } else {
            // 참가자로 입장
            room.getPlayers().add(player);
            room.setCurrentPlayers(room.getCurrentPlayers() + 1);
            log.info("플레이어 {} 참가자로 입장: {} (현재 {}/{})",
                    player.getUsername(), roomId, room.getCurrentPlayers(), room.getMaxPlayers());
        }

        return room;
    }

    /**
     * 방 나가기
     */
    public MinigameRoomDto leaveRoom(String roomId, String userId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        // 참가자 목록에서 제거
        boolean wasPlayer = room.getPlayers().removeIf(p -> p.getUserId().equals(userId));
        // 관전자 목록에서 제거
        boolean wasSpectator = room.getSpectators().removeIf(p -> p.getUserId().equals(userId));

        if (wasPlayer) {
            room.setCurrentPlayers(room.getPlayers().size());
            log.info("참가자 {} 방 나가기: {} (현재 {}/{})", userId, roomId, room.getCurrentPlayers(), room.getMaxPlayers());

            // 게임 중에 참가자가 나가서 인원이 부족한 경우
            if (room.isPlaying() && "오목".equals(room.getGameName()) && room.getPlayers().size() < 2) {
                log.info("오목 게임 중 인원 부족으로 게임 종료: roomId={}", roomId);
                room.setPlaying(false);

                // 모든 플레이어의 준비 상태 초기화
                for (MinigamePlayerDto player : room.getPlayers()) {
                    if (!player.isHost()) {
                        player.setReady(false);
                    }
                }

                // 오목 타이머 중지
                OmokGameSession omokSession = omokSessions.remove(roomId);
                if (omokSession != null && omokSession.timerFuture != null) {
                    omokSession.timerFuture.cancel(false);
                }

                // 게임 종료 이벤트 브로드캐스트
                GameEventDto gameEndEvt = new GameEventDto();
                gameEndEvt.setRoomId(roomId);
                gameEndEvt.setType("gameEndByPlayerLeave");
                gameEndEvt.setPayload("insufficient_players");
                gameEndEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", gameEndEvt);
                log.info("게임 종료 이벤트 전송: roomId={}, type=gameEndByPlayerLeave", roomId);

                // 방 상태 업데이트 브로드캐스트
                room.setAction("gameEndByPlayerLeave");
                room.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId, room);
                log.info("방 업데이트 전송: roomId={}, action=gameEndByPlayerLeave, playing={}", roomId, room.isPlaying());
            }
        } else if (wasSpectator) {
            log.info("관전자 {} 방 나가기: {} (관전자 수: {})", userId, roomId, room.getSpectators().size());
        }

        // 방장이 나갔을 때
        if (room.getHostId().equals(userId)) {
            if (room.getPlayers().isEmpty()) {
                // 방 삭제
                rooms.remove(roomId);
                log.info("방 삭제: {}", roomId);
                return null;
            } else {
                // 다음 사람을 방장으로 지정
                MinigamePlayerDto newHost = room.getPlayers().get(0);
                newHost.setHost(true);
                room.setHostId(newHost.getUserId());
                room.setHostName(newHost.getUsername());
                log.info("새로운 방장: {}", newHost.getUsername());
            }
        }

        return room;
    }

    /**
     * 방 설정 변경
     */
    public MinigameRoomDto updateRoomSettings(String roomId, String gameName, int maxPlayers) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.setGameName(gameName);

        // 최대 인원 수를 줄였을 때 초과 인원을 관전자로 이동
        if (maxPlayers < room.getMaxPlayers()) {
            int currentPlayers = room.getPlayers().size();
            if (currentPlayers > maxPlayers) {
                // 초과된 플레이어 수 계산
                int excessPlayers = currentPlayers - maxPlayers;

                // 뒤에서부터 플레이어를 관전자로 이동 (방장 제외)
                List<MinigamePlayerDto> playersToMove = new ArrayList<>();
                for (int i = room.getPlayers().size() - 1; i >= 0 && playersToMove.size() < excessPlayers; i--) {
                    MinigamePlayerDto player = room.getPlayers().get(i);
                    // 방장이 아닌 경우에만 이동
                    if (!player.isHost()) {
                        playersToMove.add(player);
                    }
                }

                // 관전자로 이동
                for (MinigamePlayerDto player : playersToMove) {
                    room.getPlayers().remove(player);
                    player.setReady(false); // 준비 상태 초기화
                    room.getSpectators().add(player);
                    log.info("플레이어 {}를 관전자로 이동: roomId={}", player.getUsername(), roomId);
                }

                // 현재 플레이어 수 업데이트
                room.setCurrentPlayers(room.getPlayers().size());
            }
        }

        room.setMaxPlayers(maxPlayers);

        log.info("방 설정 변경: {} - 게임: {}, 최대 인원: {}", roomId, gameName, maxPlayers);
        return room;
    }

    /**
     * 준비 상태 변경
     */
    public MinigameRoomDto toggleReady(String roomId, String userId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .ifPresent(p -> p.setReady(!p.isReady()));

        return room;
    }

    /**
     * 참가자 <-> 관전자 역할 전환
     */
    public MinigameRoomDto switchRole(String roomId, String userId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            log.warn("방을 찾을 수 없음: {}", roomId);
            return null;
        }

        // 현재 참가자인지 확인
        MinigamePlayerDto player = room.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        if (player != null) {
            // 참가자 -> 관전자
            boolean isHost = player.isHost();

            room.getPlayers().remove(player);
            room.setCurrentPlayers(room.getPlayers().size());

            // 관전자 리스트에 추가 (ready 상태 초기화, 방장 상태는 유지)
            player.setReady(false);
            // 방장인 경우 방장 상태 유지 (host 필드 그대로 유지)
            room.getSpectators().add(player);

            log.info("참가자 -> 관전자: userId={}, roomId={}, isHost={}", userId, roomId, isHost);
        } else {
            // 관전자인지 확인
            MinigamePlayerDto spectator = room.getSpectators().stream()
                    .filter(s -> s.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);

            if (spectator != null) {
                // 관전자 -> 참가자
                // 방이 가득 찬 경우 전환 불가
                if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
                    log.warn("방이 가득 참. 참가자로 전환 불가: userId={}, roomId={}", userId, roomId);
                    return null;
                }

                room.getSpectators().remove(spectator);
                room.getPlayers().add(spectator);
                room.setCurrentPlayers(room.getPlayers().size());

                log.info("관전자 -> 참가자: userId={}, roomId={}", userId, roomId);
            } else {
                log.warn("유저를 찾을 수 없음: userId={}, roomId={}", userId, roomId);
                return null;
            }
        }

        return room;
    }

    // Inner class to hold session state
    private static class GameSession {
        private final String roomId;
        Map<String, GameTargetDto> activeTargets = new ConcurrentHashMap<>();
        Map<String, Integer> scores = new ConcurrentHashMap<>();
        java.util.concurrent.ScheduledFuture<?> future;
        private int remainingSeconds = 0;

        public GameSession(String roomId) {
            this.roomId = roomId;
        }

        public int getRemainingSeconds() {
            return remainingSeconds;
        }

        public void setRemainingSeconds(int s) {
            remainingSeconds = s;
        }

        public void decrementRemainingSeconds() {
            remainingSeconds--;
        }
    }

    /**
     * 게임 시작
     */
    // Aiming Game Constants
    private static final int GAME_DURATION = 30; // 30초 게임
    private static final int MAX_TARGETS = 3; // 최대 3개 타겟 동시 표시
    private static final int TARGET_SPAWN_INTERVAL = 2; // 2초마다 타겟 생성 체크

    /**
     * 게임 시작
     */
    public MinigameRoomDto startGame(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.setPlaying(true);
        log.info("게임 시작: {}", roomId);

        // Initialize game session
        GameSession session = new GameSession(roomId);
        sessions.put(roomId, session);

        // Broadcast gameStart event
        GameEventDto startEvent = new GameEventDto();
        startEvent.setRoomId(roomId);
        startEvent.setType("gameStart");
        startEvent.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", startEvent);

        // 에임 맞추기 게임 로직
        if ("에임 맞추기".equals(room.getGameName())) {
            session.setRemainingSeconds(GAME_DURATION);

            // 초기 타겟 생성 (최대 3개)
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    for (int i = 0; i < MAX_TARGETS; i++) {
                        spawnTarget(roomId);
                        Thread.sleep(200); // 타겟 간 약간의 간격
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

            // 주기적으로 타겟 생성 및 타이머 업데이트
            session.future = scheduler.scheduleAtFixedRate(() -> {
                try {
                    GameSession s = sessions.get(roomId);
                    if (s == null) return;

                    // 타이머 감소
                    s.decrementRemainingSeconds();

                    // 타이머 업데이트 브로드캐스트
                    GameEventDto timerEvt = new GameEventDto();
                    timerEvt.setRoomId(roomId);
                    timerEvt.setType("aimTimer");
                    timerEvt.setPayload(String.valueOf(s.getRemainingSeconds()));
                    timerEvt.setTimestamp(System.currentTimeMillis());
                    messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                    // 타겟 생성 (최대 3개까지)
                    if (s.getRemainingSeconds() % TARGET_SPAWN_INTERVAL == 0) {
                        spawnTarget(roomId);
                    }

                    // 시간 종료 시 게임 종료
                    if (s.getRemainingSeconds() <= 0) {
                        if (s.future != null) {
                            s.future.cancel(false);
                        }
                        endGameSession(roomId);
                    }
                } catch (Exception e) {
                    log.error("에임 게임 타이머 에러: roomId={}", roomId, e);
                }
            }, 1, 1, TimeUnit.SECONDS);

            log.info("에임 게임 타이머 시작: roomId={}, duration={}s", roomId, GAME_DURATION);
        } else if ("Reaction Race".equals(room.getGameName())) {
            // Reaction Race logic
            startReactionRound(roomId);
        }

        return room;
    }

    public void sendGameState(String roomId, String userId) {
        GameSession session = sessions.get(roomId);
        if (session == null)
            return;

        // 1. Send active targets
        for (GameTargetDto target : session.activeTargets.values()) {
            GameEventDto evt = new GameEventDto();
            evt.setRoomId(roomId);
            evt.setType("spawnTarget");
            evt.setTarget(target);
            evt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", evt);
            // Note: Broadcasting to room is fine, but ideally we should send to specific
            // user
            // using separate destination like /topic/minigame/room/{roomId}/game/{userId}
            // or just rely on client filtering. But for now broadcasting "state" again to
            // everyone is messy.
            // Let's assume the frontend will dedup or we send to user specific channel if
            // possible.
            // Wait, standard STOMP pattern for user specific is /user/queue/... or specific
            // topic.
            // The user is subscribed to /topic/minigame/room/{roomId}/game.
            // If I broadcast, everyone gets it again.
            // Better: use convertAndSendToUser if we had user sessions set up affecting
            // destinations,
            // or just create a specific temporary topic.
            // However, typical simple approach: Just broadcast. Frontend "spawnTarget"
            // usually just updates map.
            // Idempotent: yes.
        }

        // 2. Send current scores
        for (Map.Entry<String, Integer> entry : session.scores.entrySet()) {
            GameEventDto scoreEvt = new GameEventDto();
            scoreEvt.setRoomId(roomId);
            scoreEvt.setType("scoreUpdate");
            scoreEvt.setPlayerId(entry.getKey());
            scoreEvt.setPayload(String.valueOf(entry.getValue()));
            scoreEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", scoreEvt);
        }
    }

    private void spawnTarget(String roomId) {
        GameSession session = sessions.get(roomId);
        if (session == null)
            return;

        // 최대 3개까지만 타겟 생성
        if (session.activeTargets.size() >= MAX_TARGETS) {
            return;
        }

        GameTargetDto target = new GameTargetDto();
        target.setId(UUID.randomUUID().toString());
        target.setX(random.nextDouble());
        target.setY(random.nextDouble());
        target.setSize(0.06 + random.nextDouble() * 0.08); // radius normalized
        target.setCreatedAt(System.currentTimeMillis());
        target.setDuration(10000); // 10s timeout, enough for players to click

        session.activeTargets.put(target.getId(), target);

        GameEventDto evt = new GameEventDto();
        evt.setRoomId(roomId);
        evt.setType("spawnTarget");
        evt.setTarget(target);
        evt.setTimestamp(System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", evt);
        log.info("타겟 생성: roomId={}, targetId={}, 현재 타겟 수={}", roomId, target.getId(), session.activeTargets.size());
    }

    private void endGameSession(String roomId) {
        GameSession session = sessions.remove(roomId);
        if (session == null)
            return;
        if (session.future != null && !session.future.isCancelled()) {
            session.future.cancel(false);
        }

        // Broadcast final scores
        Map<String, Integer> scores = session.scores;
        GameEventDto evt = new GameEventDto();
        evt.setRoomId(roomId);
        evt.setType("gameEnd");
        evt.setPayload(scores.toString());
        evt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", evt);

        // reset room playing flag and ready states
        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            // 모든 플레이어의 준비 상태 초기화 (방장 제외)
            if (room.getPlayers() != null) {
                for (MinigamePlayerDto player : room.getPlayers()) {
                    if (!player.isHost()) {
                        player.setReady(false);
                    }
                }
            }
            // 방 상태 업데이트를 모든 클라이언트에 브로드캐스트
            room.setAction("gameEnd");
            room.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId, room);
        }
    }

    public MinigameRoomDto endGameAndResetReady(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.setPlaying(false);

        // 모든 플레이어의 준비 상태 초기화 (방장 제외)
        if (room.getPlayers() != null) {
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) {
                    player.setReady(false);
                }
            }
        }

        log.info("게임 종료 및 준비 상태 초기화: {}", roomId);
        return room;
    }

    public synchronized GameScoreDto handleHit(String roomId, String playerId, String playerName, String targetId,
            long clientTs) {
        log.info("handleHit called: room={}, player={}, target={}", roomId, playerId, targetId);

        GameSession session = sessions.get(roomId);
        GameScoreDto result = new GameScoreDto();
        result.setPlayerId(playerId);
        result.setScore(0);

        if (session == null) {
            log.warn("Session not found for roomId: {}", roomId);
            return result;
        }

        GameTargetDto target = session.activeTargets.get(targetId);
        if (target == null) {
            log.warn("Target not found in session activeTargets. ID: {}", targetId);
            log.info("Current active targets: {}", session.activeTargets.keySet());
            return result; // already taken or expired
        }

        log.info("Target HIT! Removing target: {}", targetId);

        // hit successful
        session.activeTargets.remove(targetId);
        int newScore = session.scores.getOrDefault(playerId, 0) + 1;
        session.scores.put(playerId, newScore);
        result.setScore(newScore);

        log.info("New score for player {}: {}", playerId, newScore);

        // broadcast score update
        GameEventDto scoreEvt = new GameEventDto();
        scoreEvt.setRoomId(roomId);
        scoreEvt.setType("scoreUpdate");
        scoreEvt.setPlayerId(playerId);
        scoreEvt.setPlayerName(playerName);
        scoreEvt.setPayload(String.valueOf(newScore));
        scoreEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", scoreEvt);

        // broadcast target removed
        GameEventDto removed = new GameEventDto();
        removed.setRoomId(roomId);
        removed.setType("targetRemoved");
        removed.setTarget(target);
        removed.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", removed);

        // 타겟이 제거되었으므로 새로운 타겟 생성 (최대 3개까지)
        log.info("타겟 제거 후 새 타겟 생성 시도...");
        spawnTarget(roomId);

        return result;
    }

    // --- Reaction Race (MVP) ---
    public void startReactionRound(String roomId) {
        startReactionRound(roomId, false);
    }

    public void startReactionRound(String roomId, boolean immediate) {
        // send prepare
        GameEventDto prepare = new GameEventDto();
        prepare.setRoomId(roomId);
        prepare.setType("reactionPrepare");
        prepare.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", prepare);
        log.info("reactionPrepare sent for room {} (immediate={})", roomId, immediate);

        reactionActive.put(roomId, false);
        reactionWinner.remove(roomId);

        if (immediate) {
            // send GO immediately for testing
            reactionActive.put(roomId, true);
            GameEventDto goImmediate = new GameEventDto();
            goImmediate.setRoomId(roomId);
            goImmediate.setType("reactionGo");
            goImmediate.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", goImmediate);
            log.info("reactionGo (immediate) sent for room {}", roomId);

            // schedule end
            scheduler.schedule(() -> {
                reactionActive.remove(roomId);
                String winner = reactionWinner.get(roomId);
                GameEventDto end = new GameEventDto();
                end.setRoomId(roomId);
                end.setType("reactionEnd");
                end.setPayload(winner == null ? "" : winner);
                end.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", end);
                log.info("reactionEnd sent for room {} (immediate)", roomId);
            }, 3000, TimeUnit.MILLISECONDS);

            return;
        }

        // random delay then send GO
        int delayMs = 800 + random.nextInt(1800); // 800..2600ms
        log.info("Scheduling reactionGo for room {} in {}ms", roomId, delayMs);

        scheduler.schedule(() -> {
            reactionActive.put(roomId, true);
            GameEventDto go = new GameEventDto();
            go.setRoomId(roomId);
            go.setType("reactionGo");
            go.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", go);
            log.info("reactionGo sent for room {}", roomId);

            // set timeout to end reaction round
            scheduler.schedule(() -> {
                reactionActive.remove(roomId);
                String winner = reactionWinner.get(roomId);
                GameEventDto end = new GameEventDto();
                end.setRoomId(roomId);
                end.setType("reactionEnd");
                end.setPayload(winner == null ? "" : winner);
                end.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", end);
                log.info("reactionEnd sent for room {}", roomId);
            }, 3000, TimeUnit.MILLISECONDS); // 3s to respond

        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public synchronized String handleReactionHit(String roomId, String playerId, String playerName, long clientTs) {
        Boolean active = reactionActive.get(roomId);
        if (active == null || !active)
            return null;
        if (reactionWinner.get(roomId) != null)
            return null; // already have winner

        reactionWinner.put(roomId, playerName != null ? playerName : playerId);
        reactionActive.remove(roomId);

        GameEventDto res = new GameEventDto();
        res.setRoomId(roomId);
        res.setType("reactionResult");
        res.setPlayerId(playerId);
        res.setPlayerName(playerName);
        res.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", res);

        return playerName != null ? playerName : playerId;
    }

    // ===== 오목 타이머 관련 메서드 =====

    public void initOmokGame(String roomId) {
        OmokGameSession session = new OmokGameSession(roomId);
        session.board = new int[225]; // 15x15 board
        Arrays.fill(session.board, 0);
        session.moveCount = 0;
        omokSessions.put(roomId, session);
        log.info("오목 게임 초기화: roomId={}", roomId);
    }

    public void startOmokTimer(String roomId) {
        OmokGameSession session = omokSessions.get(roomId);
        if (session == null) {
            log.warn("오목 세션을 찾을 수 없음: roomId={}", roomId);
            return;
        }

        // 기존 타이머 취소
        if (session.timerFuture != null && !session.timerFuture.isCancelled()) {
            session.timerFuture.cancel(false);
        }

        session.remainingSeconds = 15;

        // 매 1초마다 타이머 업데이트 브로드캐스트
        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                OmokGameSession s = omokSessions.get(roomId);
                if (s == null)
                    return;

                s.remainingSeconds--;

                // 타이머 업데이트 브로드캐스트
                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("omokTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                // 시간 초과 시 랜덤 위치에 돌 놓기
                if (s.remainingSeconds <= 0) {
                    handleOmokTimeout(roomId);
                    if (s.timerFuture != null) {
                        s.timerFuture.cancel(false);
                    }
                }
            } catch (Exception e) {
                log.error("오목 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);

        log.info("오목 타이머 시작: roomId={}", roomId);
    }

    private void handleOmokTimeout(String roomId) {
        OmokGameSession session = omokSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null || room.getPlayers() == null || room.getPlayers().size() < 2) {
            return;
        }

        // 현재 턴 플레이어 찾기
        int currentPlayerIndex = session.moveCount % room.getPlayers().size();
        MinigamePlayerDto currentPlayer = room.getPlayers().get(currentPlayerIndex);

        // 빈 위치 찾기
        List<Integer> emptyPositions = new ArrayList<>();
        for (int i = 0; i < session.board.length; i++) {
            if (session.board[i] == 0) {
                emptyPositions.add(i);
            }
        }

        if (emptyPositions.isEmpty()) {
            log.warn("오목판에 빈 공간이 없음: roomId={}", roomId);
            return;
        }

        // 랜덤 위치 선택
        int randomPosition = emptyPositions.get(random.nextInt(emptyPositions.size()));
        int playerSymbol = currentPlayerIndex == 0 ? 1 : 2;
        session.board[randomPosition] = playerSymbol;
        session.moveCount++;

        log.info("오목 타임아웃 - 자동 배치: roomId={}, playerId={}, position={}", roomId,
                currentPlayer.getUserId(), randomPosition);

        // 자동 배치 이벤트 브로드캐스트
        GameEventDto autoMoveEvt = new GameEventDto();
        autoMoveEvt.setRoomId(roomId);
        autoMoveEvt.setType("omokMove");
        autoMoveEvt.setPlayerId(currentPlayer.getUserId());
        autoMoveEvt.setPosition(randomPosition);
        autoMoveEvt.setPayload("timeout"); // 타임아웃으로 인한 자동 배치 표시
        autoMoveEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", autoMoveEvt);

        // 다음 턴 타이머 시작
        startOmokTimer(roomId);
    }

    // 오목 게임 세션 클래스
    private static class OmokGameSession {
        private final String roomId;
        int[] board; // 15x15 = 225 cells
        int moveCount = 0;
        int remainingSeconds = 15;
        java.util.concurrent.ScheduledFuture<?> timerFuture;
        Set<String> rematchRequests = new HashSet<>(); // 다시하기 요청한 플레이어 ID

        public OmokGameSession(String roomId) {
            this.roomId = roomId;
        }
    }

    /**
     * 오목 다시하기 요청 추가
     * @return 모든 플레이어가 동의했으면 true
     */
    public boolean addOmokRematchRequest(String roomId, String playerId) {
        OmokGameSession session = omokSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);

        if (session == null || room == null) {
            return false;
        }

        // 다시하기 요청 추가
        session.rematchRequests.add(playerId);
        log.info("오목 다시하기 요청 추가: roomId={}, playerId={}, 현재 요청 수={}/{}",
                 roomId, playerId, session.rematchRequests.size(), room.getPlayers().size());

        // 모든 플레이어가 동의했는지 확인
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            // 요청 초기화
            session.rematchRequests.clear();
            return true;
        }

        return false;
    }
}
