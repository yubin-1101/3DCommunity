import React, { useState, useEffect, useRef } from 'react';
import './GlobalChat.css';
import multiplayerService from '../services/multiplayerService';

function GlobalChat({ isVisible = true, username, userId, onlineCount: externalOnlineCount, playerJoinEvent, playerLeaveEvent, onInputFocusChange, onChatMessage, isPhoneUIOpen = false }) {
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [isMinimized, setIsMinimized] = useState(false);
  const [onlineCount, setOnlineCount] = useState(0);
  const [activeTab, setActiveTab] = useState('all'); // 'all', 'plaza', 'system'
  const [isFocused, setIsFocused] = useState(false);
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  // 외부에서 전달받은 온라인 카운트 사용
  useEffect(() => {
    if (externalOnlineCount !== undefined) {
      setOnlineCount(externalOnlineCount);
    }
  }, [externalOnlineCount]);

  // 플레이어 입장 이벤트 처리
  useEffect(() => {
    if (playerJoinEvent) {
      const systemMessage = {
        id: `sys_join_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        username: 'System',
        userId: 'system',
        text: `${playerJoinEvent.username}님이 입장하셨습니다.`,
        timestamp: new Date().toLocaleTimeString('ko-KR', {
          hour: '2-digit',
          minute: '2-digit'
        }),
        isSystem: true
      };
      setMessages(prev => [...prev, systemMessage]);
    }
  }, [playerJoinEvent]);

  // 플레이어 퇴장 이벤트 처리
  useEffect(() => {
    if (playerLeaveEvent) {
      const systemMessage = {
        id: `sys_leave_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        username: 'System',
        userId: 'system',
        text: `${playerLeaveEvent.username}님이 퇴장하셨습니다.`,
        timestamp: new Date().toLocaleTimeString('ko-KR', {
          hour: '2-digit',
          minute: '2-digit'
        }),
        isSystem: true
      };
      setMessages(prev => [...prev, systemMessage]);
    }
  }, [playerLeaveEvent]);

  // 키보드 이벤트 처리 (Enter로 채팅 활성화, ESC로 비활성화)
  useEffect(() => {
    const handleKeyDown = (e) => {
      // 다른 input이 포커스되어 있으면 무시 (ChatRoom input 등)
      const isInputFocused = document.activeElement &&
                            (document.activeElement.tagName === 'INPUT' ||
                             document.activeElement.tagName === 'TEXTAREA');

      // PhoneUI가 열려있거나 다른 input이 포커스되어 있으면 Enter 키를 무시
      if (e.key === 'Enter' && !isFocused && isVisible && !isPhoneUIOpen && !isInputFocused) {
        e.preventDefault();
        // 최소화 상태면 먼저 확장
        if (isMinimized) {
          setIsMinimized(false);
        }
        // 입력창 포커스 (확장 후 포커스되도록 setTimeout 사용)
        setTimeout(() => {
          inputRef.current?.focus();
        }, 100);
      } else if (e.key === 'Escape' && isFocused) {
        e.preventDefault();
        inputRef.current?.blur();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isFocused, isVisible, isMinimized, isPhoneUIOpen]);

  // 포커스 상태 변경 시 부모에게 알림
  useEffect(() => {
    onInputFocusChange?.(isFocused);
  }, [isFocused, onInputFocusChange]);

  // 자동 스크롤
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // WebSocket 채팅 메시지 수신
  useEffect(() => {
    const handleChatMessage = (data) => {
      const newMessage = {
        id: `${data.userId}_${data.timestamp || Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        username: data.username,
        userId: data.userId,
        text: data.message,
        timestamp: new Date(data.timestamp).toLocaleTimeString('ko-KR', {
          hour: '2-digit',
          minute: '2-digit'
        })
      };
      setMessages(prev => [...prev, newMessage]);

      // 부모 컴포넌트에게도 전달 (말풍선 표시용)
      if (onChatMessage) {
        onChatMessage(data);
      }
    };

    const cleanup = multiplayerService.onChatMessage(handleChatMessage);

    return cleanup; // Return the cleanup function properly
  }, [onChatMessage]);

  const handleSendMessage = (e) => {
    e.preventDefault();
    if (inputText.trim() && multiplayerService.connected) {
      // WebSocket으로 메시지 전송
      multiplayerService.sendChatMessage(inputText.trim());
      setInputText('');
    }
  };

  // 탭에 따라 메시지 필터링
  const filteredMessages = messages.filter((msg) => {
    if (activeTab === 'all') return true;
    if (activeTab === 'plaza') return !msg.isSystem;
    if (activeTab === 'system') return msg.isSystem;
    return true;
  });

  if (!isVisible) return null;

  return (
    <div className={`global-chat-container ${isMinimized ? 'minimized' : ''}`}>
      {/* 헤더 */}
      <div className="global-chat-header">
        <div className="header-left">
          <span className="chat-icon">💬</span>
          <span className="chat-title">전체 채팅</span>
          <span className="online-count">• {onlineCount} online</span>
        </div>
        <div className="header-buttons">
          <button
            className="minimize-button"
            onClick={() => setIsMinimized(!isMinimized)}
          >
            {isMinimized ? '▲' : '▼'}
          </button>
        </div>
      </div>

      {/* 탭 메뉴 */}
      {!isMinimized && (
        <div className="chat-tabs">
          <button
            className={`chat-tab ${activeTab === 'all' ? 'active' : ''}`}
            onClick={() => setActiveTab('all')}
          >
            전체
          </button>
          <button
            className={`chat-tab ${activeTab === 'plaza' ? 'active' : ''}`}
            onClick={() => setActiveTab('plaza')}
          >
            광장
          </button>
          <button
            className={`chat-tab ${activeTab === 'system' ? 'active' : ''}`}
            onClick={() => setActiveTab('system')}
          >
            시스템
          </button>
        </div>
      )}

      {/* 채팅 메시지 영역 */}
      {!isMinimized && (
        <>
          <div className="global-chat-messages">
            {filteredMessages.length === 0 && (
              <div className="chat-empty-message">
                채팅 메시지가 없습니다. 첫 메시지를 보내보세요!
              </div>
            )}
            {filteredMessages.map((msg) => (
              <div
                key={msg.id}
                className={`chat-message ${msg.isSystem ? 'system-message' : ''} ${String(msg.userId) === String(userId) ? 'my-message' : ''}`}
              >
                {!msg.isSystem && (
                  <div className="message-header">
                    <span className="message-username">
                      {String(msg.userId) === String(userId) ? '나' : msg.username}
                    </span>
                    <span className="message-timestamp">{msg.timestamp}</span>
                  </div>
                )}
                <div className="message-text">{msg.text}</div>
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>

          {/* 입력 영역 */}
          <form className="global-chat-input-container" onSubmit={handleSendMessage}>
            <input
              ref={inputRef}
              type="text"
              className="global-chat-input"
              placeholder="메시지를 입력하세요... (Enter)"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onFocus={() => setIsFocused(true)}
              onBlur={() => setIsFocused(false)}
              maxLength={200}
            />
            <button type="submit" className="send-button">
              ➤
            </button>
          </form>
        </>
      )}
    </div>
  );
}

export default GlobalChat;
