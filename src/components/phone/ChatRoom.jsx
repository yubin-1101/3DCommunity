import React, { useState, useEffect, useRef } from 'react';
import './ChatRoom.css';
import { chatAPI } from '../../services/chatService';
import multiplayerService from '../../services/multiplayerService';
import Popup from '../Popup';
import ProfileAvatar from '../ProfileAvatar';

function ChatRoom({ chat, currentUserId, currentUsername, onBack, onSendMessage }) {
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [popupMessage, setPopupMessage] = useState(null);
  const [typingUsernames, setTypingUsernames] = useState([]); // 이름 목록으로 관리
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);
  const isTypingRef = useRef(false);

  const roomId = chat.roomId || chat.id;

  const [isConnected, setIsConnected] = useState(multiplayerService.isConnected());

  // WebSocket 연결 상태 모니터링
  useEffect(() => {
    const cleanup = multiplayerService.onConnect((connected) => {
      setIsConnected(connected);
    });
    return cleanup;
  }, []);

  useEffect(() => {
    if (!roomId) return;
    loadMessages();

    // WebSocket이 연결된 상태일 때만 구독
    if (isConnected) {
      console.log(`[ChatRoom] Subscribing to room ${roomId}`);
      const subscription = multiplayerService.subscribeToRoom(roomId, (packet) => {
        console.log('[ChatRoom] WebSocket Packet Received:', packet);

        if (packet.type === 'TYPING') {
          handleTypingIndicator(packet.data);
        } else if (packet.type === 'MESSAGE' || packet.data) {
          // 일반 메시지 수신 (백엔드 구조: {type: 'MESSAGE', data: messageDto})
          const msgData = packet.data || packet;

          const newMessage = {
            id: msgData.id,
            senderId: msgData.senderId,
            senderName: msgData.senderName || msgData.username,
            content: msgData.content || msgData.message,
            timestamp: msgData.createdAt ? new Date(msgData.createdAt) : new Date(),
            isMine: String(msgData.senderId) === String(currentUserId),
          };

          console.log('[ChatRoom] New message added to state:', newMessage);

          setMessages(prev => {
            if (prev.some(m => m.id === newMessage.id)) return prev;
            return [...prev, newMessage];
          });

          if (String(msgData.senderId) !== String(currentUserId)) {
            chatAPI.markAsRead(roomId, msgData.id).catch(console.error);
          }
        }
      });

      return () => {
        if (subscription) subscription.unsubscribe();
        multiplayerService.unsubscribeFromRoom(roomId);
      };
    }
  }, [roomId, isConnected]);

  const handleTypingIndicator = (data) => {
    if (String(data.userId) === String(currentUserId)) return;

    const typerName = data.username || "상대방";
    if (data.isTyping) {
      setTypingUsernames(prev => prev.includes(typerName) ? prev : [...prev, typerName]);
    } else {
      setTypingUsernames(prev => prev.filter(name => name !== typerName));
    }
  };

  const loadMessages = async () => {
    try {
      setLoading(true);
      const response = await chatAPI.getMessages(roomId);
      const data = response.data;

      setMessages(data.map(msg => ({
        id: msg.id,
        senderId: msg.senderId || msg.userId,
        senderName: msg.senderName || msg.username || '알 수 없음',
        content: msg.content || msg.message,
        timestamp: msg.createdAt ? new Date(msg.createdAt) : (msg.timestamp ? new Date(msg.timestamp) : new Date()),
        isMine: String(msg.senderId || msg.userId) === String(currentUserId),
      })).reverse()); // 최신순으로 오므로 역순 정렬

      if (data.length > 0) {
        // data[0]이 가장 최근 메시지이므로 (DESC 정렬) 0번 인덱스 사용
        await chatAPI.markAsRead(roomId, data[0].id);
      }
      setTimeout(scrollToBottom, 100);
    } catch (error) {
      console.error('로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { scrollToBottom(); }, [messages]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const handleSend = async () => {
    if (!inputMessage.trim()) return;
    const content = inputMessage.trim();
    try {
      console.log('[ChatRoom] Sending message...');
      // WebSocket으로 먼저 시도
      const sent = multiplayerService.sendRoomMessage(roomId, content);
      console.log('[ChatRoom] WebSocket send result:', sent);

      // WebSocket 연결이 안되어있거나 실패하면 REST API로 전송
      if (!sent) {
        console.log('[ChatRoom] Falling back to REST API');
        await chatAPI.sendMessage(roomId, content);
      }

      multiplayerService.sendTypingIndicator(roomId, false);
      isTypingRef.current = false;
      setInputMessage('');
    } catch (error) {
      console.error('[ChatRoom] Send failed:', error);
      setPopupMessage('전송 실패');
    }
  };

  const handleInputChange = (e) => {
    const value = e.target.value;
    setInputMessage(value);

    // 글자가 있으면 true, 없으면 false
    const shouldBeTyping = value.length > 0;

    // 상태가 변할 때만 WebSocket 메시지 전송 (스팸 방지)
    if (shouldBeTyping !== isTypingRef.current) {
      isTypingRef.current = shouldBeTyping;
      multiplayerService.sendTypingIndicator(roomId, shouldBeTyping);
    }
  };

  return (
    <div className="chat-room-container">
      <div className="chat-room-header">
        <button className="back-btn" onClick={onBack}>←</button>
        <ProfileAvatar
          profileImage={{ imagePath: chat.profileImagePath }}
          outlineImage={{ imagePath: chat.outlineImagePath }}
          size={45}
        />
        <div className="chat-room-info">
          <div className="chat-room-name">{chat.friendName || chat.title}</div>
          <div className="chat-room-status">
            {chat.type === 'GROUP' ? '그룹 채팅' : (chat.isOnline ? '온라인' : '오프라인')}
          </div>
        </div>
      </div>

      <div className="messages-container">
        {messages.map((message, index) => (
          <div key={message.id || index} className={`message-wrapper ${message.isMine ? 'mine' : 'theirs'}`}>
            {!message.isMine && (
              <ProfileAvatar
                profileImage={{ imagePath: chat.profileImagePath }}
                outlineImage={{ imagePath: chat.outlineImagePath }}
                size={35}
              />
            )}
            <div className="message-content">
              {!message.isMine && <div className="message-sender">{message.senderName}</div>}
              <div className="message-bubble">
                <div className="message-text">{message.content}</div>
              </div>
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {/* 요구하신 타이핑 표시 영역 */}
      {typingUsernames.length > 0 && (
        <div className="typing-status-text">
          {typingUsernames.join(', ')}님이 입력 중입니다...
        </div>
      )}

      <div className="message-input-container">
        <input
          ref={inputRef}
          type="text"
          className="message-input"
          placeholder="메시지 입력..."
          value={inputMessage}
          onChange={handleInputChange}
          onKeyDown={(e) => e.key === 'Enter' && handleSend()}
        />
        <button className="send-btn" onClick={handleSend} disabled={!inputMessage.trim()}>➤</button>
      </div>
      {popupMessage && <Popup message={popupMessage} onClose={() => setPopupMessage(null)} />}
    </div>
  );
}

export default ChatRoom;