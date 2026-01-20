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

    // ===== 끝말잇기 게임 관련 =====
    private final Map<String, WordChainSession> wordChainSessions = new ConcurrentHashMap<>();

    private static class WordChainSession {
        String roomId;
        List<String> wordHistory = new ArrayList<>();
        String currentWord;
        int currentPlayerIndex = 0;
        int remainingSeconds = 10;
        java.util.concurrent.ScheduledFuture<?> timerFuture;
        Set<String> rematchRequests = new HashSet<>();

        public WordChainSession(String roomId) {
            this.roomId = roomId;
        }
    }

    public void initWordChainGame(String roomId, String startWord) {
        WordChainSession session = new WordChainSession(roomId);
        session.currentWord = startWord;
        session.wordHistory.add(startWord);
        session.currentPlayerIndex = 0;
        wordChainSessions.put(roomId, session);
        log.info("끝말잇기 게임 시작: roomId={}, startWord={}", roomId, startWord);
    }

    public void startWordChainTimer(String roomId) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return;

        // 기존 타이머 취소
        if (session.timerFuture != null && !session.timerFuture.isCancelled()) {
            session.timerFuture.cancel(false);
        }

        session.remainingSeconds = 10;

        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                WordChainSession s = wordChainSessions.get(roomId);
                if (s == null) return;

                s.remainingSeconds--;

                // 타이머 업데이트 브로드캐스트
                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("wordChainTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                // 시간 초과 시 현재 플레이어 패배
                if (s.remainingSeconds <= 0) {
                    if (s.timerFuture != null) {
                        s.timerFuture.cancel(false);
                    }
                    handleWordChainTimeout(roomId);
                }
            } catch (Exception e) {
                log.error("끝말잇기 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void handleWordChainTimeout(String roomId) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return;

        MinigamePlayerDto loser = room.getPlayers().get(session.currentPlayerIndex);

        // 게임 종료 이벤트
        GameEventDto endEvt = new GameEventDto();
        endEvt.setRoomId(roomId);
        endEvt.setType("wordChainEnd");
        endEvt.setPlayerId(loser.getUserId());
        endEvt.setPlayerName(loser.getUsername());
        endEvt.setPayload("timeout");
        endEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", endEvt);

        endWordChainGame(roomId);
    }

    public boolean submitWord(String roomId, String playerId, String word) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return false;

        // 현재 턴인지 확인
        MinigamePlayerDto currentPlayer = room.getPlayers().get(session.currentPlayerIndex);
        if (!currentPlayer.getUserId().equals(playerId)) {
            return false;
        }

        // 끝말잇기 규칙 검증
        String lastChar = getLastChar(session.currentWord);
        String firstChar = word.substring(0, 1);

        // 두음법칙 적용
        String convertedLastChar = applyDueum(lastChar);

        if (!firstChar.equals(lastChar) && !firstChar.equals(convertedLastChar)) {
            // 첫 글자가 맞지 않음
            GameEventDto errorEvt = new GameEventDto();
            errorEvt.setRoomId(roomId);
            errorEvt.setType("wordChainError");
            errorEvt.setPlayerId(playerId);
            errorEvt.setPayload("'" + convertedLastChar + "'(으)로 시작하는 단어를 입력하세요");
            errorEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", errorEvt);
            return false;
        }

        // 이미 사용한 단어인지 확인
        if (session.wordHistory.contains(word)) {
            GameEventDto errorEvt = new GameEventDto();
            errorEvt.setRoomId(roomId);
            errorEvt.setType("wordChainError");
            errorEvt.setPlayerId(playerId);
            errorEvt.setPayload("이미 사용한 단어입니다");
            errorEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", errorEvt);
            return false;
        }

        // 단어 저장
        session.wordHistory.add(word);
        session.currentWord = word;

        // 다음 플레이어로
        session.currentPlayerIndex = (session.currentPlayerIndex + 1) % room.getPlayers().size();

        // 성공 이벤트
        GameEventDto wordEvt = new GameEventDto();
        wordEvt.setRoomId(roomId);
        wordEvt.setType("wordChainWord");
        wordEvt.setPlayerId(playerId);
        wordEvt.setPlayerName(currentPlayer.getUsername());
        wordEvt.setPayload(word);
        wordEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", wordEvt);

        // 타이머 재시작
        startWordChainTimer(roomId);

        return true;
    }

    private String getLastChar(String word) {
        return word.substring(word.length() - 1);
    }

    private String applyDueum(String ch) {
        // 두음법칙 적용
        Map<String, String> dueumMap = new HashMap<>();
        dueumMap.put("녀", "여");
        dueumMap.put("뇨", "요");
        dueumMap.put("뉴", "유");
        dueumMap.put("니", "이");
        dueumMap.put("랴", "야");
        dueumMap.put("려", "여");
        dueumMap.put("례", "예");
        dueumMap.put("료", "요");
        dueumMap.put("류", "유");
        dueumMap.put("리", "이");
        dueumMap.put("라", "나");
        dueumMap.put("래", "내");
        dueumMap.put("로", "노");
        dueumMap.put("뢰", "뇌");
        dueumMap.put("루", "누");
        dueumMap.put("르", "느");
        return dueumMap.getOrDefault(ch, ch);
    }

    private void endWordChainGame(String roomId) {
        WordChainSession session = wordChainSessions.remove(roomId);
        if (session != null && session.timerFuture != null) {
            session.timerFuture.cancel(false);
        }

        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) player.setReady(false);
            }
        }
    }

    public boolean addWordChainRematchRequest(String roomId, String playerId) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null) {
            session = new WordChainSession(roomId);
            wordChainSessions.put(roomId, session);
        }
        if (room == null) return false;

        session.rematchRequests.add(playerId);
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            session.rematchRequests.clear();
            return true;
        }
        return false;
    }

    // ===== 스무고개 게임 관련 =====
    private final Map<String, TwentyQSession> twentyQSessions = new ConcurrentHashMap<>();

    private static class TwentyQSession {
        String roomId;
        String questionerId;
        String questionerName;
        String category;
        String answer;
        int questionCount = 0;
        List<Map<String, Object>> history = new ArrayList<>();
        String currentAsker;
        String pendingQuestion;
        Set<String> rematchRequests = new HashSet<>();

        public TwentyQSession(String roomId) {
            this.roomId = roomId;
        }
    }

    public void initTwentyQGame(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null || room.getPlayers().isEmpty()) return;

        TwentyQSession session = new TwentyQSession(roomId);
        // 첫 번째 플레이어가 출제자
        MinigamePlayerDto questioner = room.getPlayers().get(0);
        session.questionerId = questioner.getUserId();
        session.questionerName = questioner.getUsername();

        twentyQSessions.put(roomId, session);
        log.info("스무고개 게임 초기화: roomId={}, questioner={}", roomId, session.questionerName);

        // 출제자에게 단어 선택 요청
        GameEventDto startEvt = new GameEventDto();
        startEvt.setRoomId(roomId);
        startEvt.setType("twentyQStart");
        startEvt.setPlayerId(session.questionerId);
        startEvt.setPlayerName(session.questionerName);
        startEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", startEvt);
    }

    public void setTwentyQWord(String roomId, String playerId, String category, String word) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || !session.questionerId.equals(playerId)) return;

        session.category = category;
        session.answer = word;

        // 단어 선택 완료 알림
        GameEventDto selectedEvt = new GameEventDto();
        selectedEvt.setRoomId(roomId);
        selectedEvt.setType("twentyQWordSelected");
        selectedEvt.setPayload(category);
        selectedEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", selectedEvt);

        log.info("스무고개 단어 설정: roomId={}, category={}, word={}", roomId, category, word);
    }

    public void submitTwentyQQuestion(String roomId, String playerId, String playerName, String question) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || session.answer == null) return;
        if (session.questionerId.equals(playerId)) return; // 출제자는 질문 불가

        session.questionCount++;
        session.pendingQuestion = question;
        session.currentAsker = playerId;

        // 질문 전송
        GameEventDto questionEvt = new GameEventDto();
        questionEvt.setRoomId(roomId);
        questionEvt.setType("twentyQQuestion");
        questionEvt.setPlayerId(playerId);
        questionEvt.setPlayerName(playerName);
        questionEvt.setPayload(question);
        questionEvt.setPosition(session.questionCount);
        questionEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", questionEvt);
    }

    public void answerTwentyQQuestion(String roomId, String playerId, boolean isYes) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || !session.questionerId.equals(playerId)) return;

        Map<String, Object> historyItem = new HashMap<>();
        historyItem.put("question", session.pendingQuestion);
        historyItem.put("answer", isYes);
        historyItem.put("questionNumber", session.questionCount);
        session.history.add(historyItem);

        // 답변 전송
        GameEventDto answerEvt = new GameEventDto();
        answerEvt.setRoomId(roomId);
        answerEvt.setType("twentyQAnswer");
        answerEvt.setPlayerId(session.currentAsker);
        answerEvt.setPayload(isYes ? "yes" : "no");
        answerEvt.setPosition(session.questionCount);
        answerEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", answerEvt);

        // 20개 질문 소진 시 게임 종료
        if (session.questionCount >= 20) {
            endTwentyQGame(roomId, null, false);
        }
    }

    public void guessTwentyQAnswer(String roomId, String playerId, String playerName, String guess) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || session.answer == null) return;

        boolean correct = session.answer.equalsIgnoreCase(guess.trim());

        // 추측 결과 전송
        GameEventDto guessEvt = new GameEventDto();
        guessEvt.setRoomId(roomId);
        guessEvt.setType("twentyQGuess");
        guessEvt.setPlayerId(playerId);
        guessEvt.setPlayerName(playerName);
        guessEvt.setPayload(guess);
        guessEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", guessEvt);

        if (correct) {
            endTwentyQGame(roomId, playerId, true);
        }
    }

    private void endTwentyQGame(String roomId, String winnerId, boolean guessed) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null) return;

        GameEventDto endEvt = new GameEventDto();
        endEvt.setRoomId(roomId);
        endEvt.setType("twentyQEnd");
        endEvt.setPlayerId(winnerId);
        endEvt.setPayload(session.answer);
        endEvt.setPosition(session.questionCount);
        endEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", endEvt);

        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) player.setReady(false);
            }
        }
    }

    public boolean addTwentyQRematchRequest(String roomId, String playerId) {
        TwentyQSession session = twentyQSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null) {
            session = new TwentyQSession(roomId);
            twentyQSessions.put(roomId, session);
        }
        if (room == null) return false;

        session.rematchRequests.add(playerId);
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            session.rematchRequests.clear();
            twentyQSessions.remove(roomId);
            return true;
        }
        return false;
    }

    // ===== 라이어 게임 관련 =====
    private final Map<String, LiarGameSession> liarSessions = new ConcurrentHashMap<>();

    private static class LiarGameSession {
        String roomId;
        String liarId;
        String liarName;
        String category;
        String keyword;
        Map<String, String> votes = new HashMap<>();
        int remainingSeconds = 0;
        java.util.concurrent.ScheduledFuture<?> timerFuture;
        Set<String> rematchRequests = new HashSet<>();
        boolean liarCaught = false;
        String liarGuess = null;

        public LiarGameSession(String roomId) {
            this.roomId = roomId;
        }
    }

    // 라이어 게임 카테고리 및 단어
    private static final Map<String, List<String>> LIAR_WORDS = new HashMap<>();
    static {
        LIAR_WORDS.put("동물", Arrays.asList("사자", "호랑이", "코끼리", "기린", "펭귄", "돌고래", "독수리", "판다"));
        LIAR_WORDS.put("음식", Arrays.asList("피자", "햄버거", "스파게티", "초밥", "김치찌개", "불고기", "떡볶이", "치킨"));
        LIAR_WORDS.put("직업", Arrays.asList("의사", "변호사", "소방관", "요리사", "선생님", "경찰관", "프로그래머", "디자이너"));
        LIAR_WORDS.put("장소", Arrays.asList("학교", "병원", "공원", "도서관", "영화관", "마트", "해변", "산"));
        LIAR_WORDS.put("스포츠", Arrays.asList("축구", "농구", "야구", "테니스", "수영", "골프", "배드민턴", "탁구"));
    }

    public void initLiarGame(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null || room.getPlayers().size() < 4) return;

        LiarGameSession session = new LiarGameSession(roomId);

        // 랜덤으로 라이어 선택
        int liarIndex = random.nextInt(room.getPlayers().size());
        MinigamePlayerDto liar = room.getPlayers().get(liarIndex);
        session.liarId = liar.getUserId();
        session.liarName = liar.getUsername();

        // 랜덤 카테고리와 단어 선택
        List<String> categories = new ArrayList<>(LIAR_WORDS.keySet());
        session.category = categories.get(random.nextInt(categories.size()));
        List<String> words = LIAR_WORDS.get(session.category);
        session.keyword = words.get(random.nextInt(words.size()));

        liarSessions.put(roomId, session);
        log.info("라이어 게임 시작: roomId={}, liar={}, category={}, keyword={}",
                roomId, session.liarName, session.category, session.keyword);

        // 각 플레이어에게 역할 전송
        for (MinigamePlayerDto player : room.getPlayers()) {
            GameEventDto roleEvt = new GameEventDto();
            roleEvt.setRoomId(roomId);
            roleEvt.setType("liarGameStart");
            roleEvt.setPlayerId(player.getUserId());

            Map<String, String> roleData = new HashMap<>();
            roleData.put("category", session.category);
            if (player.getUserId().equals(session.liarId)) {
                roleData.put("role", "liar");
                roleData.put("keyword", "???");
            } else {
                roleData.put("role", "citizen");
                roleData.put("keyword", session.keyword);
            }
            roleEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(roleData).toString());
            roleEvt.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game/" + player.getUserId(), roleEvt);
        }

        // 5초 후 토론 시작
        scheduler.schedule(() -> startLiarDiscussion(roomId), 5, TimeUnit.SECONDS);
    }

    private void startLiarDiscussion(String roomId) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        session.remainingSeconds = 60;

        // 토론 시작 알림
        GameEventDto discussEvt = new GameEventDto();
        discussEvt.setRoomId(roomId);
        discussEvt.setType("liarDiscussionStart");
        discussEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", discussEvt);

        // 타이머 시작
        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                LiarGameSession s = liarSessions.get(roomId);
                if (s == null) return;

                s.remainingSeconds--;

                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("liarTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                if (s.remainingSeconds <= 0) {
                    if (s.timerFuture != null) s.timerFuture.cancel(false);
                    startLiarVoting(roomId);
                }
            } catch (Exception e) {
                log.error("라이어 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void startLiarVoting(String roomId) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        session.votes.clear();
        session.remainingSeconds = 15;

        // 투표 시작 알림
        GameEventDto voteEvt = new GameEventDto();
        voteEvt.setRoomId(roomId);
        voteEvt.setType("liarVotingStart");
        voteEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", voteEvt);

        // 투표 타이머
        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                LiarGameSession s = liarSessions.get(roomId);
                if (s == null) return;

                s.remainingSeconds--;

                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("liarTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                if (s.remainingSeconds <= 0) {
                    if (s.timerFuture != null) s.timerFuture.cancel(false);
                    processLiarVotes(roomId);
                }
            } catch (Exception e) {
                log.error("라이어 투표 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void submitLiarVote(String roomId, String voterId, String targetId) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        session.votes.put(voterId, targetId);
        log.info("라이어 투표: roomId={}, voter={}, target={}", roomId, voterId, targetId);

        // 투표 현황 브로드캐스트
        GameEventDto voteEvt = new GameEventDto();
        voteEvt.setRoomId(roomId);
        voteEvt.setType("liarVote");
        voteEvt.setPlayerId(voterId);
        voteEvt.setPayload(targetId);
        voteEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", voteEvt);

        // 모든 플레이어가 투표했는지 확인
        MinigameRoomDto room = rooms.get(roomId);
        if (room != null && session.votes.size() >= room.getPlayers().size()) {
            if (session.timerFuture != null) session.timerFuture.cancel(false);
            processLiarVotes(roomId);
        }
    }

    private void processLiarVotes(String roomId) {
        LiarGameSession session = liarSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return;

        // 득표수 계산
        Map<String, Integer> voteCounts = new HashMap<>();
        for (String targetId : session.votes.values()) {
            voteCounts.merge(targetId, 1, Integer::sum);
        }

        // 최다 득표자 찾기
        String mostVotedId = null;
        int maxVotes = 0;
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                mostVotedId = entry.getKey();
            }
        }

        // 라이어가 잡혔는지 확인
        boolean liarCaught = mostVotedId != null && mostVotedId.equals(session.liarId);
        session.liarCaught = liarCaught;

        // 투표 결과 전송
        GameEventDto resultEvt = new GameEventDto();
        resultEvt.setRoomId(roomId);
        resultEvt.setType("liarVoteResult");
        resultEvt.setPlayerId(mostVotedId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("votedPlayerId", mostVotedId);
        resultData.put("voteCount", maxVotes);
        resultData.put("liarCaught", liarCaught);
        resultData.put("liarId", session.liarId);
        resultData.put("liarName", session.liarName);
        resultData.put("voteCounts", voteCounts);

        try {
            resultEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(resultData));
        } catch (Exception e) {
            resultEvt.setPayload(resultData.toString());
        }
        resultEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", resultEvt);

        // 라이어가 잡혔으면 키워드 맞추기 기회 (10초)
        if (liarCaught) {
            session.remainingSeconds = 10;
            session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
                try {
                    LiarGameSession s = liarSessions.get(roomId);
                    if (s == null) return;
                    s.remainingSeconds--;

                    GameEventDto timerEvt = new GameEventDto();
                    timerEvt.setRoomId(roomId);
                    timerEvt.setType("liarTimer");
                    timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                    timerEvt.setTimestamp(System.currentTimeMillis());
                    messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                    if (s.remainingSeconds <= 0) {
                        if (s.timerFuture != null) s.timerFuture.cancel(false);
                        endLiarGame(roomId, false); // 라이어가 맞추지 못함
                    }
                } catch (Exception e) {
                    log.error("라이어 키워드 맞추기 타이머 에러: roomId={}", roomId, e);
                }
            }, 1, 1, TimeUnit.SECONDS);
        } else {
            // 라이어가 못 잡혔으면 라이어 승리
            endLiarGame(roomId, true);
        }
    }

    public void submitLiarGuess(String roomId, String playerId, String guess) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null || !session.liarId.equals(playerId)) return;

        session.liarGuess = guess;
        boolean correct = session.keyword.equals(guess.trim());

        if (session.timerFuture != null) session.timerFuture.cancel(false);

        // 추측 결과 전송
        GameEventDto guessEvt = new GameEventDto();
        guessEvt.setRoomId(roomId);
        guessEvt.setType("liarGuessResult");
        guessEvt.setPlayerId(playerId);

        Map<String, Object> guessData = new HashMap<>();
        guessData.put("guess", guess);
        guessData.put("correct", correct);
        guessData.put("keyword", session.keyword);

        try {
            guessEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(guessData));
        } catch (Exception e) {
            guessEvt.setPayload(guessData.toString());
        }
        guessEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", guessEvt);

        // 라이어가 키워드를 맞추면 라이어 승리
        endLiarGame(roomId, correct);
    }

    private void endLiarGame(String roomId, boolean liarWins) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        if (session.timerFuture != null) session.timerFuture.cancel(false);

        GameEventDto endEvt = new GameEventDto();
        endEvt.setRoomId(roomId);
        endEvt.setType("liarGameEnd");

        Map<String, Object> endData = new HashMap<>();
        endData.put("liarWins", liarWins);
        endData.put("liarId", session.liarId);
        endData.put("liarName", session.liarName);
        endData.put("keyword", session.keyword);
        endData.put("category", session.category);

        try {
            endEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(endData));
        } catch (Exception e) {
            endEvt.setPayload(endData.toString());
        }
        endEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", endEvt);

        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) player.setReady(false);
            }
        }
    }

    public void sendLiarChat(String roomId, String playerId, String playerName, String message) {
        GameEventDto chatEvt = new GameEventDto();
        chatEvt.setRoomId(roomId);
        chatEvt.setType("liarChat");
        chatEvt.setPlayerId(playerId);
        chatEvt.setPlayerName(playerName);
        chatEvt.setPayload(message);
        chatEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", chatEvt);
    }

    public boolean addLiarRematchRequest(String roomId, String playerId) {
        LiarGameSession session = liarSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null) {
            session = new LiarGameSession(roomId);
            liarSessions.put(roomId, session);
        }
        if (room == null) return false;

        session.rematchRequests.add(playerId);
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            session.rematchRequests.clear();
            liarSessions.remove(roomId);
            return true;
        }
        return false;
    }
}
