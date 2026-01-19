import React, { useState, useEffect } from 'react';
import './ChatList.css';
import ChatRoom from './ChatRoom';
import { chatAPI } from '../../services/chatService';
import multiplayerService from '../../services/multiplayerService';
import ProfileAvatar from '../ProfileAvatar';

function ChatList({ userId, username, onlinePlayers, onUnreadCountChange, initialFriend, onChatOpened }) {
  const [chatRooms, setChatRooms] = useState([]);
  const [selectedChat, setSelectedChat] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadConversations();

    // WebSocket 알림 구독 (새 메시지 또는 읽음 처리 시 목록 갱신)
    if (userId && multiplayerService.isConnected()) {
      console.log('[ChatList] Subscribing to user updates for ID:', userId);
      const subscription = multiplayerService.subscribeToUserUpdates(userId, (update) => {
        console.log('[ChatList] User update received:', update);
        if (update.type === 'NEW_MESSAGE' || update.type === 'READ_UPDATE') {
          loadConversations();
        }
      });
      return () => {
        if (subscription) subscription.unsubscribe();
      };
    }
  }, [userId]);

  useEffect(() => {
    const totalUnread = chatRooms.reduce((sum, room) => sum + (room.unreadCount || 0), 0);
    if (onUnreadCountChange) onUnreadCountChange(totalUnread);
  }, [chatRooms, onUnreadCountChange]);

  // 초기 진입 처리 (DM 알림, 친구 목록에서 진입 등)
  useEffect(() => {
    const initChat = async () => {
      if (initialFriend) {
        console.log('initChat 호출됨. initialFriend:', initialFriend);
        try {
          setLoading(true);

          if (!initialFriend.friendId) {
            console.error('오류: initialFriend.friendId가 없습니다.', initialFriend);
            return;
          }

          // 친구 ID로 방을 찾거나 생성 요청 (DM 타입)
          console.log('방 생성 요청 시작 (friendId):', initialFriend.friendId);
          const response = await chatAPI.createRoom('DM', [initialFriend.friendId]);
          console.log('방 생성 응답:', response.data);

          const room = response.data;

          // Room 정보를 UI 포맷에 맞게 변환
          const chatRoomData = {
            id: room.id,
            roomId: room.id,
            type: room.type,
            title: room.title || initialFriend.friendName, // 백엔드 제목 우선 사용
            friendName: room.title || initialFriend.friendName,
            friendId: initialFriend.friendId,
            lastMessage: room.lastMessage,
            lastMessageTime: room.updatedAt ? new Date(room.updatedAt) : new Date(),
            unreadCount: 0, // 막 들어왔으므로 0
            profileImagePath: initialFriend.profileImagePath,
            outlineImagePath: initialFriend.outlineImagePath,
            isOnline: false // 추후 연동 필요
          };

          setSelectedChat(chatRoomData);
          if (onChatOpened) onChatOpened();
        } catch (error) {
          console.error('채팅방 초기화 실패:', error);
          if (error.response) {
            console.error('서버 에러 응답:', error.response.data);
            console.error('서버 에러 메시지:', error.response.data.message);
          }
        } finally {
          setLoading(false);
        }
      }
    };

    initChat();
  }, [initialFriend]); // onChatOpened는 의존성에서 제외 (무한루프 방지)

  const loadConversations = async () => {
    try {
      setLoading(true);
      const response = await chatAPI.getRooms();
      const data = response.data;

      const formattedData = data.map(room => ({
        id: room.id,
        roomId: room.id,
        type: room.type,
        title: room.title,
        // 1:1인 경우 상대방 이름을 노출하기 위한 로직 (백엔드 DTO에 따라 조정 필요)
        friendName: room.title,
        lastMessage: room.lastMessage || '대화를 시작해보세요!',
        lastMessageTime: room.updatedAt ? new Date(room.updatedAt) : null,
        unreadCount: room.unreadCount || 0,
        // 기존 UI 필드 유지
        profileImagePath: room.friendProfileImagePath,
        outlineImagePath: room.friendOutlineImagePath,
      }));
      setChatRooms(formattedData);
    } catch (error) {
      console.error('목록 로드 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  if (selectedChat) {
    return (
      <ChatRoom
        chat={selectedChat}
        currentUserId={userId}
        currentUsername={username}
        onBack={() => { setSelectedChat(null); loadConversations(); }}
        onSendMessage={() => { }} // ChatRoom 내부에서 직접 처리함
      />
    );
  }

  return (
    <div className="chat-list-container">
      {loading ? (
        <div className="empty-state"><div className="empty-text">로딩 중...</div></div>
      ) : chatRooms.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">💬</div>
          <div className="empty-text">대화 내역이 없습니다.</div>
        </div>
      ) : (
        <div className="chat-rooms">
          {chatRooms.map(chat => (
            <div key={chat.id} className="chat-room-item" onClick={() => setSelectedChat(chat)}>
              <div className="chat-avatar-wrapper">
                <ProfileAvatar
                  profileImage={{ imagePath: chat.profileImagePath }}
                  outlineImage={{ imagePath: chat.outlineImagePath }}
                  size={50}
                />
              </div>
              <div className="chat-content">
                <div className="chat-header">
                  <div className="chat-name">{chat.title}</div>
                  <div className="chat-time">
                    {chat.lastMessageTime ? chat.lastMessageTime.toLocaleDateString() : ''}
                  </div>
                </div>
                <div className="chat-preview">
                  <div className="chat-last-message">{chat.lastMessage}</div>
                  {chat.unreadCount > 0 && <div className="chat-unread-badge">{chat.unreadCount}</div>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default ChatList;