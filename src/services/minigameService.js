import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class MinigameService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.userId = null;
    this.username = null;
    this.currentRoomId = null;
    this.subscribedRooms = new Set(); // 구독한 방 ID 추적

    // Event Listeners (Map<EventType, Set<Callback>>)
    this.listeners = {
      roomsList: new Set(),
      roomUpdate: new Set(),
      roomJoin: new Set(),
      roomLeave: new Set(),
      roomChat: new Set(),
      roomDelete: new Set(),
      gameInvite: new Set(),
      gameEvent: new Set(),
      joinResult: new Set(),
      connectionStatus: new Set() // 연결 상태 변경 이벤트
    };
  }

  connect(userId, username, timeoutMs = 10000) {
    // If already connected, resolve immediately
    if (this.connected && this.client && this.client.active) {
      console.log('⚠️ Minigame already connected, skipping reconnect');
      return Promise.resolve();
    }

    this.userId = userId;
    this.username = username;

    const wsUrl = process.env.REACT_APP_SOCKET_URL || 'http://localhost:8080';
    // const socket = new SockJS(`${wsUrl}/ws`); // Move inside factory to avoid race condition

    // Create a promise that will resolve when onConnect is called
    return new Promise((resolve, reject) => {
      let settled = false;
      const timer = setTimeout(() => {
        if (!settled) {
          settled = true;
          reject(new Error('Minigame WebSocket connection timeout'));
        }
      }, timeoutMs);

      this.client = new Client({
        webSocketFactory: () => {
          console.log('[MinigameService] Creating SockJS instance...');
          return new SockJS(`${wsUrl}/ws`);
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: () => { },
        onConnect: () => {
          console.log('✅ Minigame WebSocket Connected');
          this.connected = true;
          this.emit('connectionStatus', { connected: true }); // 연결 상태 전달

          // 방 목록 업데이트 구독
          this.client.subscribe('/topic/minigame/rooms', (message) => {
            const data = JSON.parse(message.body);

            if (data.action === 'create' || data.action === 'update' || data.action === 'join' || data.action === 'leave' || data.action === 'switchRole' || data.action === 'start') {
              this.emit('roomUpdate', data);
            } else if (data.action === 'delete') {
              this.emit('roomDelete', data);
            }
          });

          // 방 목록 전체 구독
          this.client.subscribe('/topic/minigame/rooms-list', (message) => {
            const data = JSON.parse(message.body);
            this.emit('roomsList', data);
          });

          // 개인 게임 초대 구독
          this.client.subscribe('/topic/minigame/invite/' + this.userId, (message) => {
            const data = JSON.parse(message.body);
            this.emit('gameInvite', data);
          });

          // 개인 입장 결과(ACK) 구독
          this.client.subscribe('/topic/minigame/joinResult/' + this.userId, (message) => {
            const data = JSON.parse(message.body);
            this.emit('joinResult', data);
          });

          // 초기 방 목록 요청
          this.requestRoomsList();

          if (!settled) {
            settled = true;
            clearTimeout(timer);
            resolve();
          }
        },
        onStompError: (frame) => {
          console.error('❌ Minigame STOMP Error:', frame.headers['message']);
          console.error('Details:', frame.body);
        },
        onWebSocketClose: () => {
          console.log('⚠️ Minigame WebSocket Closed');
          this.connected = false;
          this.emit('connectionStatus', { connected: false }); // 연결 상태 전달
        }
      });

      this.client.activate();
    });
  }

  disconnect() {
    if (this.client && this.connected) {
      // 현재 방에 있다면 나가기
      if (this.currentRoomId) {
        this.leaveRoom(this.currentRoomId);
      }
      this.client.deactivate();
      this.connected = false;
      this.subscribedRooms.clear(); // 모든 구독 목록 초기화
      console.log('🔌 Minigame WebSocket Disconnected');
    }
  }

  /**
   * 방 목록 요청
   */
  requestRoomsList() {
    if (this.connected && this.client) {
      this.client.publish({
        destination: '/app/minigame.rooms.list'
      });
    }
  }

  /**
   * 방 생성
   */
  createRoom(roomName, gameName, maxPlayers, isLocked, hostLevel, selectedProfile, selectedOutline, gpsLocation = null) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomName,
      gameName,
      hostId: this.userId,
      hostName: this.username,
      maxPlayers,
      isLocked,
      hostLevel: hostLevel || 1,
      selectedProfile: selectedProfile || null,
      selectedOutline: selectedOutline || null,
      // GPS 위치 정보 추가
      gpsLng: gpsLocation ? gpsLocation[0] : null,
      gpsLat: gpsLocation ? gpsLocation[1] : null
    };

    this.client.publish({
      destination: '/app/minigame.room.create',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 방 입장
   */
  joinRoom(roomId, level, selectedProfile, selectedOutline) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomId,
      userId: this.userId,
      username: this.username,
      level,
      selectedProfile: selectedProfile || null,
      selectedOutline: selectedOutline || null
    };

    // 먼저 방을 구독해서 서버가 즉시 브로드캐스트할 때 놓치지 않도록 함
    this.subscribeToRoom(roomId);
    this.currentRoomId = roomId;

    this.client.publish({
      destination: '/app/minigame.room.join',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 방 구독
   */
  subscribeToRoom(roomId) {
    if (!this.connected || !this.client) {
      return;
    }

    // 이미 구독한 방이면 중복 구독 방지
    if (this.subscribedRooms.has(roomId)) {
      console.log('⚠️ Already subscribed to room:', roomId);
      return;
    }

    console.log('📡 Subscribing to room:', roomId);
    this.subscribedRooms.add(roomId);

    // 방 업데이트 구독
    this.client.subscribe('/topic/minigame/room/' + roomId, (message) => {
      const data = JSON.parse(message.body);
      this.emit('roomJoin', data); // Reuse roomJoin or add roomUpdate specific listener
    });

    // 방 채팅 구독
    this.client.subscribe('/topic/minigame/room/' + roomId + '/chat', (message) => {
      const data = JSON.parse(message.body);
      this.emit('roomChat', data);
    });

    // 게임 이벤트(타겟 스폰, 점수 업데이트 등) 구독
    this.client.subscribe('/topic/minigame/room/' + roomId + '/game', (message) => {
      const data = JSON.parse(message.body);
      this.emit('gameEvent', data);
    });
  }

  /**
   * 방 나가기
   */
  leaveRoom(roomId) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomId,
      userId: this.userId
    };

    this.client.publish({
      destination: '/app/minigame.room.leave',
      body: JSON.stringify(payload)
    });

    this.currentRoomId = null;
    this.subscribedRooms.delete(roomId); // 구독 목록에서 제거
  }

  /**
   * 방 설정 변경
   */
  updateRoom(roomId, gameName, maxPlayers) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomId,
      gameName,
      maxPlayers
    };

    this.client.publish({
      destination: '/app/minigame.room.update',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 준비 상태 토글
   */
  toggleReady(roomId) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomId,
      userId: this.userId
    };

    this.client.publish({
      destination: '/app/minigame.room.ready',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 역할 전환 (참가자 <-> 관전자)
   */
  switchRole(roomId) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomId,
      userId: this.userId
    };

    this.client.publish({
      destination: '/app/minigame.room.switchRole',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 게임 시작
   */
  startGame(roomId) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomId
    };

    this.client.publish({
      destination: '/app/minigame.room.start',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 방 채팅 메시지 전송
   */
  sendRoomChat(roomId, message) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      roomId,
      userId: this.userId,
      username: this.username,
      message
    };

    this.client.publish({
      destination: '/app/minigame.room.chat',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 게임 이벤트 전송 (hit 등)
   */
  sendGameEvent(roomId, event) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      ...event,
      roomId,
      playerId: this.userId,
      playerName: this.username,
      timestamp: Date.now()
    };

    this.client.publish({
      destination: '/app/minigame.room.game',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 과녁 맞춤 처리
   */
  handleHit(roomId, playerId, playerName, targetId, timestamp) {
    this.sendGameEvent(roomId, {
      type: 'hit',
      targetId,
      playerId,
      playerName, // Optional, depending on backend requirement
      clientTimestamp: timestamp
    });
  }

  /**
   * 게임 상태 요청 (동기화)
   */
  requestGameState(roomId) {
    if (!this.connected || !this.client) return;

    const payload = {
      roomId,
      playerId: this.userId,
      type: 'stateRequest'
    };

    this.client.publish({
      destination: '/app/minigame.room.state',
      body: JSON.stringify(payload)
    });
  }

  /**
   * 게임 초대 전송
   */
  sendGameInvite(targetUserId, targetUsername, roomId, gameName) {
    if (!this.connected || !this.client) {
      console.error('WebSocket not connected');
      return;
    }

    const payload = {
      inviterId: this.userId,
      inviterUsername: this.username,
      targetUserId,
      targetUsername,
      roomId,
      gameName
    };

    this.client.publish({
      destination: '/app/minigame.invite',
      body: JSON.stringify(payload)
    });

  }

  /**
   * 콜백 등록 (Observer Pattern)
   */
  on(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].add(callback);
    } else {
      console.warn('Unknown event type:', event);
    }
  }

  off(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].delete(callback);
    }
  }

  emit(event, data) {
    if (this.listeners[event]) {
      this.listeners[event].forEach(callback => {
        try {
          callback(data);
        } catch (err) {
          console.error(`Error in listener for ${event}:`, err);
        }
      });
    }
  }
}

const minigameService = new MinigameService();
export default minigameService;
