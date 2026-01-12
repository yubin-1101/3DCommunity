package com.community.controller;

import com.community.dto.*;
import com.community.service.MinigameRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MinigameController {

    private final MinigameRoomService roomService;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * 방 생성
     * Client -> /app/minigame.room.create
     * Server -> /topic/minigame/rooms (broadcast)
     */
    @MessageMapping("/minigame.room.create")
    @SendTo("/topic/minigame/rooms")
    public MinigameRoomDto createRoom(CreateRoomRequest request) {
        log.info("방 생성 요청: {}", request);

        MinigameRoomDto room = roomService.createRoom(
                request.getRoomName(),
                request.getGameName(),
                request.getHostId(),
                request.getHostName(),
                request.getMaxPlayers(),
                request.isLocked(),
                request.getHostLevel(),
                request.getSelectedProfile(),
                request.getSelectedOutline(),
                request.getGpsLng(),
                request.getGpsLat());
        room.setAction("create");
        room.setTimestamp(System.currentTimeMillis());

        return room;
    }

    /**
     * 방 목록 요청
     * Client -> /app/minigame.rooms.list
     * Server -> /topic/minigame/rooms-list (to sender)
     */
    @MessageMapping("/minigame.rooms.list")
    public void getRoomsList() {
        List<MinigameRoomDto> rooms = roomService.getAllRooms();
        messagingTemplate.convertAndSend("/topic/minigame/rooms-list", rooms);
    }

    /**
     * 방 입장
     * Client -> /app/minigame.room.join
     * Server -> /topic/minigame/room/{roomId} (to room)
     */
    @MessageMapping("/minigame.room.join")
    public void joinRoom(JoinRoomRequest request) {
        log.info("방 입장 요청: {}", request);

        MinigamePlayerDto player = new MinigamePlayerDto();
        player.setUserId(request.getUserId());
        player.setUsername(request.getUsername());
        player.setLevel(request.getLevel());
        player.setHost(false);
        player.setReady(false);
        player.setSelectedProfile(request.getSelectedProfile());
        player.setSelectedOutline(request.getSelectedOutline());

        MinigameRoomDto room = roomService.joinRoom(request.getRoomId(), player);
        if (room != null) {
            room.setAction("join");
            room.setTimestamp(System.currentTimeMillis());

            // 방에 있는 모든 사람에게 브로드캐스트
            messagingTemplate.convertAndSend("/topic/minigame/room/" + request.getRoomId(), room);

            // 방 목록 업데이트 브로드캐스트
            messagingTemplate.convertAndSend("/topic/minigame/rooms", room);

            // 개인에게도 성공 ACK 전송 (so joining client gets explicit confirmation)
            GameEventDto ack = new GameEventDto();
            ack.setRoomId(request.getRoomId());
            ack.setType("joinResult");
            ack.setPlayerId(request.getUserId());
            ack.setPayload("ok");
            ack.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/joinResult/" + request.getUserId(), ack);
            log.info("joinResult(ok) sent to user {} for room {}", request.getUserId(), request.getRoomId());
        } else {
            // failure reason checking
            MinigameRoomDto maybeRoom = roomService.getRoom(request.getRoomId());
            String reason = "not found or full";
            if (maybeRoom == null)
                reason = "room not found";
            else if (maybeRoom.getCurrentPlayers() >= maybeRoom.getMaxPlayers())
                reason = "room full";

            // 실패(방 없음 또는 가득 참)일 때 개인에게 오류 ACK 전송
            GameEventDto ack = new GameEventDto();
            ack.setRoomId(request.getRoomId());
            ack.setType("joinResult");
            ack.setPlayerId(request.getUserId());
            ack.setPayload("error: " + reason);
            ack.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/joinResult/" + request.getUserId(), ack);
            log.warn("joinResult(error: {}) sent to user {} for room {}", reason, request.getUserId(),
                    request.getRoomId());
        }
    }

    /**
     * 방 나가기
     * Client -> /app/minigame.room.leave
     * Server -> /topic/minigame/room/{roomId} (to room)
     */
    @MessageMapping("/minigame.room.leave")
    public void leaveRoom(RoomActionRequest request) {
        log.info("방 나가기 요청: {}", request);

        MinigameRoomDto room = roomService.leaveRoom(request.getRoomId(), request.getUserId());

        if (room == null) {
            // 방이 삭제됨
            MinigameRoomDto deletedRoom = new MinigameRoomDto();
            deletedRoom.setRoomId(request.getRoomId());
            deletedRoom.setAction("delete");
            deletedRoom.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/minigame/rooms", deletedRoom);
        } else {
            // 서비스에서 이미 gameEndByPlayerLeave 액션을 설정한 경우 덮어쓰지 않음
            if (!"gameEndByPlayerLeave".equals(room.getAction())) {
                room.setAction("leave");
                room.setTimestamp(System.currentTimeMillis());

                // 방에 있는 모든 사람에게 브로드캐스트
                messagingTemplate.convertAndSend("/topic/minigame/room/" + request.getRoomId(), room);

                // 방 목록 업데이트 브로드캐스트
                messagingTemplate.convertAndSend("/topic/minigame/rooms", room);
                log.info("일반 나가기 처리: roomId={}, action=leave", request.getRoomId());
            } else {
                log.info("gameEndByPlayerLeave 액션 감지 - 중복 브로드캐스트 스킵: roomId={}", request.getRoomId());
            }
        }
    }

    /**
     * 방 설정 변경
     * Client -> /app/minigame.room.update
     * Server -> /topic/minigame/room/{roomId} (to room)
     */
    @MessageMapping("/minigame.room.update")
    public void updateRoom(UpdateRoomRequest request) {
        log.info("방 설정 변경 요청: {}", request);

        MinigameRoomDto room = roomService.updateRoomSettings(
                request.getRoomId(),
                request.getGameName(),
                request.getMaxPlayers());
        if (room != null) {
            room.setAction("update");
            room.setTimestamp(System.currentTimeMillis());

            // 방에 있는 모든 사람에게 브로드캐스트
            messagingTemplate.convertAndSend("/topic/minigame/room/" + request.getRoomId(), room);

            // 방 목록 업데이트 브로드캐스트
            messagingTemplate.convertAndSend("/topic/minigame/rooms", room);
        }
    }

    /**
     * 준비 상태 변경
     * Client -> /app/minigame.room.ready
     * Server -> /topic/minigame/room/{roomId} (to room)
     */
    @MessageMapping("/minigame.room.ready")
    public void toggleReady(RoomActionRequest request) {
        log.info("준비 상태 변경 요청: {}", request);

        MinigameRoomDto room = roomService.toggleReady(request.getRoomId(), request.getUserId());
        if (room != null) {
            room.setAction("ready");
            room.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/minigame/room/" + request.getRoomId(), room);
        }
    }

    /**
     * 역할 전환 (참가자 <-> 관전자)
     * Client -> /app/minigame.room.switchRole
     * Server -> /topic/minigame/room/{roomId} (to room)
     */
    @MessageMapping("/minigame.room.switchRole")
    public void switchRole(RoomActionRequest request) {
        log.info("역할 전환 요청: roomId={}, userId={}", request.getRoomId(), request.getUserId());

        MinigameRoomDto room = roomService.switchRole(request.getRoomId(), request.getUserId());
        if (room != null) {
            room.setAction("switchRole");
            room.setTimestamp(System.currentTimeMillis());

            // 방에 있는 모든 사람에게 브로드캐스트
            messagingTemplate.convertAndSend("/topic/minigame/room/" + request.getRoomId(), room);

            // 방 목록 업데이트 브로드캐스트
            messagingTemplate.convertAndSend("/topic/minigame/rooms", room);
        } else {
            log.warn("역할 전환 실패: roomId={}, userId={}", request.getRoomId(), request.getUserId());
        }
    }

    /**
     * 게임 시작
     * Client -> /app/minigame.room.start
     * Server -> /topic/minigame/room/{roomId} (to room)
     */
    @MessageMapping("/minigame.room.start")
    public void startGame(RoomActionRequest request) {
        log.info("게임 시작 요청: {}", request);

        MinigameRoomDto room = roomService.startGame(request.getRoomId());
        if (room != null) {
            room.setAction("start");
            room.setTimestamp(System.currentTimeMillis());

            // 방에 있는 모든 사람에게 브로드캐스트
            messagingTemplate.convertAndSend("/topic/minigame/room/" + request.getRoomId(), room);

            // 로비에 있는 사람들에게도 방 상태 업데이트 브로드캐스트 (대기중 -> 게임중)
            messagingTemplate.convertAndSend("/topic/minigame/rooms", room);
        }
    }

    /**
     * 게임 이벤트 (spawn, hit 등)
     * Client -> /app/minigame.room.game
     * Server -> /topic/minigame/room/{roomId}/game (to room)
     */
    @MessageMapping("/minigame.room.game")
    public void handleGameEvent(GameEventDto event) {
        log.info("게임 이벤트 수신: {}", event);
        if (event == null || event.getRoomId() == null)
            return;

        if ("hit".equals(event.getType())) {
            // validate and update score
            String roomId = event.getRoomId();
            String playerId = event.getPlayerId();
            String playerName = event.getPlayerName();
            String targetId = event.getTarget() != null ? event.getTarget().getId() : event.getTargetId();

            GameScoreDto result = roomService.handleHit(roomId, playerId, playerName, targetId,
                    event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp());

            // send back an acknowledgement (scoreUpdate already broadcast by service)
            if (result != null) {
                GameEventDto scoreEvt = new GameEventDto();
                scoreEvt.setRoomId(roomId);
                scoreEvt.setType("hitAck");
                scoreEvt.setPlayerId(playerId);
                scoreEvt.setPayload(String.valueOf(result.getScore()));
                scoreEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", scoreEvt);
            }
        }

        if ("omokMove".equals(event.getType())) {
            // 오목 움직임 처리
            String roomId = event.getRoomId();
            String playerId = event.getPlayerId();
            Integer position = event.getPosition();

            log.info("오목 움직임: roomId={}, playerId={}, position={}", roomId, playerId, position);

            // 타이머 재시작 (다음 턴)
            roomService.startOmokTimer(roomId);

            // 모든 플레이어에게 브로드캐스트
            GameEventDto omokEvt = new GameEventDto();
            omokEvt.setRoomId(roomId);
            omokEvt.setType("omokMove");
            omokEvt.setPlayerId(playerId);
            omokEvt.setPosition(position);
            omokEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", omokEvt);
        }

        if ("omokStart".equals(event.getType())) {
            // 오목 게임 시작 시 첫 타이머 시작
            String roomId = event.getRoomId();
            log.info("오목 게임 시작, 타이머 시작: roomId={}", roomId);
            roomService.initOmokGame(roomId);
            roomService.startOmokTimer(roomId);
        }

        if ("omokRematchRequest".equals(event.getType())) {
            // 다시하기 요청
            String roomId = event.getRoomId();
            String playerId = event.getPlayerId();
            log.info("오목 다시하기 요청: roomId={}, playerId={}", roomId, playerId);

            // 다시하기 요청 브로드캐스트
            GameEventDto rematchReqEvt = new GameEventDto();
            rematchReqEvt.setRoomId(roomId);
            rematchReqEvt.setType("omokRematchRequest");
            rematchReqEvt.setPlayerId(playerId);
            rematchReqEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", rematchReqEvt);

            // 모든 플레이어가 동의했는지 확인
            boolean allAgreed = roomService.addOmokRematchRequest(roomId, playerId);
            if (allAgreed) {
                log.info("모든 플레이어 동의 - 오목 게임 재시작: roomId={}", roomId);

                // 게임 재시작
                roomService.initOmokGame(roomId);
                roomService.startOmokTimer(roomId);

                // 재시작 이벤트 브로드캐스트
                GameEventDto rematchStartEvt = new GameEventDto();
                rematchStartEvt.setRoomId(roomId);
                rematchStartEvt.setType("omokRematchStart");
                rematchStartEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", rematchStartEvt);
            }
        }

        if ("countdownStart".equals(event.getType())) {
            // 카운트다운 시작 이벤트 처리
            String roomId = event.getRoomId();
            String hostId = event.getPlayerId();

            log.info("카운트다운 시작: roomId={}, hostId={}", roomId, hostId);

            // 모든 플레이어에게 브로드캐스트
            GameEventDto countdownEvt = new GameEventDto();
            countdownEvt.setRoomId(roomId);
            countdownEvt.setType("countdownStart");
            countdownEvt.setPlayerId(hostId);
            countdownEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", countdownEvt);
        }

        if ("backToWaiting".equals(event.getType())) {
            // 대기방으로 돌아가기 이벤트 처리
            String roomId = event.getRoomId();

            log.info("대기방으로 돌아가기: roomId={}", roomId);

            // 방 상태 업데이트
            MinigameRoomDto room = roomService.endGameAndResetReady(roomId);
            if (room != null) {
                room.setAction("backToWaiting");
                room.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId, room);
            }
        }

        if ("reactionStart".equals(event.getType())) {
            String roomId = event.getRoomId();
            boolean immediate = false;
            if (event.getPayload() != null && event.getPayload().contains("immediate")) {
                immediate = true;
            }
            log.info("reactionStart received for room {} (immediate={})", roomId, immediate);
            roomService.startReactionRound(roomId, immediate);
        }

        if ("reactionHit".equals(event.getType())) {
            String roomId = event.getRoomId();
            String playerId = event.getPlayerId();
            String playerName = event.getPlayerName();
            roomService.handleReactionHit(roomId, playerId, playerName,
                    event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp());
        }
    }

    /**
     * 게임 상태 요청 (재접속/새로고침 시 동기화)
     */
    @MessageMapping("/minigame.room.state")
    public void requestGameState(GameEventDto event) {
        if (event == null || event.getRoomId() == null)
            return;
        log.info("게임 상태 요청: roomId={}, userId={}", event.getRoomId(), event.getPlayerId());
        roomService.sendGameState(event.getRoomId(), event.getPlayerId());
    }

    /**
     * 대기방 채팅
     * Client -> /app/minigame.room.chat
     * Server -> /topic/minigame/room/{roomId}/chat (to room)
     */
    @MessageMapping("/minigame.room.chat")
    public void sendRoomChat(MinigameChatDto chatDto) {
        chatDto.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + chatDto.getRoomId() + "/chat", chatDto);
    }

    /**
     * 게임 초대
     * Client -> /app/minigame.invite
     * Server -> /topic/minigame/invite/{targetUserId} (to target user)
     */
    @MessageMapping("/minigame.invite")
    public void sendGameInvite(GameInviteDto inviteDto) {
        log.info("게임 초대 전송: {} -> {}", inviteDto.getInviterUsername(), inviteDto.getTargetUsername());

        inviteDto.setTimestamp(System.currentTimeMillis());

        // 초대 받는 사람에게만 전송
        messagingTemplate.convertAndSend("/topic/minigame/invite/" + inviteDto.getTargetUserId(), inviteDto);
    }
}
