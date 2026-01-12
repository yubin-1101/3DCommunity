import React, { useState, useEffect } from 'react';
import './PersonalRoomModal.css';
import PersonalRoomChat from './map/PersonalRoomChat';
import multiplayerService from '../services/multiplayerService';

/**
 * 개인 룸 모달 컴포넌트 (간소화 버전)
 * - 방 생성 버튼 클릭 시 바로 내 개인 룸 생성
 * - 기존에 내 방이 있으면 기존 방으로 입장
 * - 친구 목록에서 초대
 */
function PersonalRoomModal({ 
  onClose, 
  userProfile, 
  friends = [], 
  mode = 'create', // 'create', 'waiting', 'browse'
  currentRoom = null,
  onCreateRoom,
  onInviteFriend,
  onLeaveRoom,
  onJoinRoom
}) {
  const [currentMode, setCurrentMode] = useState(mode);
  const [selectedFriends, setSelectedFriends] = useState([]);
  const [invitedFriends, setInvitedFriends] = useState([]);
  const [roomMembers, setRoomMembers] = useState([]);
  const [availableRooms, setAvailableRooms] = useState([]);
  const [myRoom, setMyRoom] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  // 이미 방 생성 시도했는지 추적
  const [hasAttemptedCreate, setHasAttemptedCreate] = useState(false);

  // 실시간 방 목록 업데이트 구독
  useEffect(() => {
    if (currentMode !== 'browse') return;
    
    console.log('📡 방 목록 실시간 구독 시작');
    const unsubscribe = multiplayerService.onRoomListUpdate((rooms) => {
      console.log('📡 방 목록 실시간 업데이트 수신:', rooms.length, 'rooms');
      setAvailableRooms(rooms);
    });
    
    return () => {
      console.log('📡 방 목록 실시간 구독 종료');
      unsubscribe?.();
    };
  }, [currentMode]);

  // 초기화 - 'create' 모드면 기존 방 확인 후 방 생성 또는 입장
  useEffect(() => {
    const checkAndCreateRoom = async () => {
      // 이미 방이 있거나, 생성 시도를 했거나, 유저 정보가 없으면 스킵
      if (mode !== 'create' || myRoom || hasAttemptedCreate || !userProfile?.id) {
        if (mode === 'browse') {
          setCurrentMode('browse');
        } else if (currentRoom) {
          setMyRoom(currentRoom);
          setRoomMembers(currentRoom.members || [userProfile]);
          setCurrentMode('waiting');
        }
        return;
      }
      
      setHasAttemptedCreate(true); // 중복 실행 방지
      
      if (mode === 'create' && userProfile?.id) {
        setIsLoading(true);
        
        try {
          // 기존 방이 있는지 확인
          const existingData = await multiplayerService.checkHasRoom(userProfile.id);
          
          if (existingData.hasRoom && existingData.room) {
            // 기존 방이 있으면 그 방으로 입장
            console.log('🏠 기존 방 발견, 입장:', existingData.room);
            const existingRoom = {
              ...existingData.room,
              members: [userProfile],
            };
            setMyRoom(existingRoom);
            setRoomMembers([userProfile]);
            setCurrentMode('waiting');
            
            // 부모 컴포넌트에 알림
            setTimeout(() => {
              console.log('📢 기존 방으로 입장:', existingRoom);
              onCreateRoom?.(existingRoom);
            }, 100);
          } else {
            // 기존 방이 없으면 서버에 새 방 생성 요청
            const newRoomData = {
              roomId: `room_${userProfile.id}_${Date.now()}`,
              roomName: `${userProfile.username || '나'}의 방`,
              hostId: userProfile.id,
              hostName: userProfile.username,
              maxMembers: 6,
              isPrivate: true,
            };
            
            console.log('🏠 서버에 새 개인 룸 생성 요청:', newRoomData);
            
            // 서버에 방 생성 API 호출
            const serverResponse = await multiplayerService.createPersonalRoom(newRoomData);
            
            // 서버 응답에서 roomId 사용 (기존 방이 있으면 기존 방의 ID가 반환됨)
            const finalRoomData = {
              ...newRoomData,
              roomId: serverResponse?.roomId || newRoomData.roomId,
              roomName: serverResponse?.roomName || newRoomData.roomName,
              members: [userProfile],
              createdAt: new Date().toISOString()
            };
            
            console.log('🏠 최종 방 데이터:', finalRoomData);
            setMyRoom(finalRoomData);
            setRoomMembers([userProfile]);
            setCurrentMode('waiting');
            
            // 부모 컴포넌트에 알림
            setTimeout(() => {
              console.log('📢 부모 컴포넌트에 방 생성 알림:', finalRoomData);
              onCreateRoom?.(finalRoomData);
            }, 100);
          }
        } catch (error) {
          console.error('방 확인/생성 중 오류:', error);
          // 오류 발생 시에도 기존 방 확인 재시도
          try {
            const retryData = await multiplayerService.checkHasRoom(userProfile.id);
            if (retryData.hasRoom && retryData.room) {
              console.log('🏠 재시도 - 기존 방 발견:', retryData.room);
              const existingRoom = {
                ...retryData.room,
                members: [userProfile],
              };
              setMyRoom(existingRoom);
              setRoomMembers([userProfile]);
              setCurrentMode('waiting');
              setTimeout(() => {
                onCreateRoom?.(existingRoom);
              }, 100);
              return;
            }
          } catch (retryError) {
            console.error('재시도 실패:', retryError);
          }
          
          // 재시도도 실패하면 로컬로만 방 생성
          const roomData = {
            roomId: `room_${userProfile.id}_${Date.now()}`,
            roomName: `${userProfile.username || '나'}의 방`,
            hostId: userProfile.id,
            hostName: userProfile.username,
            maxMembers: 6,
            isPrivate: true,
            members: [userProfile],
            createdAt: new Date().toISOString()
          };
          
          console.log('🏠 개인 룸 생성 (폴백):', roomData);
          setMyRoom(roomData);
          setRoomMembers([userProfile]);
          setCurrentMode('waiting');
          
          setTimeout(() => {
            onCreateRoom?.(roomData);
          }, 100);
        } finally {
          setIsLoading(false);
        }
      }
    };
    
    checkAndCreateRoom();
  }, [mode, userProfile, currentRoom, onCreateRoom, myRoom, hasAttemptedCreate]);

  // 친구 선택 토글
  const toggleFriendSelection = (friend) => {
    setSelectedFriends(prev => {
      const isSelected = prev.some(f => f.id === friend.id);
      if (isSelected) {
        return prev.filter(f => f.id !== friend.id);
      } else {
        return [...prev, friend];
      }
    });
  };

  // 친구 초대
  const handleInviteFriends = () => {
    if (selectedFriends.length === 0) return;
    
    selectedFriends.forEach(friend => {
      console.log('📨 친구 초대:', friend.username);
      onInviteFriend?.(friend);
    });

    setInvitedFriends(prev => [...prev, ...selectedFriends]);
    setSelectedFriends([]);
  };

  // 방 나가기
  const handleLeaveRoom = () => {
    onLeaveRoom?.();
    onClose();
  };

  // 공개 방 입장
  const handleJoinPublicRoom = (room) => {
    onJoinRoom?.(room);
    setMyRoom(room);
    setRoomMembers(room.members || []);
    setCurrentMode('waiting');
  };

  return (
    <div className="personal-room-modal-overlay" onClick={onClose}>
      <div className="personal-room-modal" onClick={e => e.stopPropagation()}>
        {/* 헤더 */}
        <div className="personal-room-header">
          <h2>
            {isLoading && '⏳ 방 확인 중...'}
            {!isLoading && currentMode === 'waiting' && '🏠 내 개인 룸'}
            {!isLoading && currentMode === 'browse' && '🔍 공개 룸 찾기'}
          </h2>
          <button className="close-btn" onClick={onClose}>✕</button>
        </div>

        {/* 로딩 중 */}
        {isLoading && (
          <div className="personal-room-content" style={{ textAlign: 'center', padding: '40px 20px' }}>
            <div className="loading-spinner" style={{
              width: 40,
              height: 40,
              border: '4px solid rgba(255,255,255,0.2)',
              borderTop: '4px solid #00bcd4',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite',
              margin: '0 auto 20px'
            }} />
            <p style={{ color: '#aaa' }}>기존 방이 있는지 확인하고 있습니다...</p>
            <style>{`
              @keyframes spin {
                0% { transform: rotate(0deg); }
                100% { transform: rotate(360deg); }
              }
            `}</style>
          </div>
        )}

        {/* 대기실 모드 */}
        {!isLoading && currentMode === 'waiting' && (
          <div className="personal-room-content">
            <div className="room-info-banner">
              <span className="room-name-display">{myRoom?.roomName || `${userProfile?.username}의 방`}</span>
              <span className="room-members-count">
                👥 {roomMembers.length}명 참여 중
              </span>
            </div>

            {/* 현재 멤버 목록 */}
            <div className="members-section">
              <h3>👥 참여 중인 멤버</h3>
              <div className="members-list">
                {roomMembers.map((member, idx) => (
                  <div key={member?.id || idx} className="member-card">
                    <div className="member-avatar">
                      {member?.selectedProfile ? (
                        <img src={member.selectedProfile} alt="" />
                      ) : (
                        <span className="default-avatar">👤</span>
                      )}
                    </div>
                    <div className="member-info">
                      <span className="member-name">{member?.username || '알 수 없음'}</span>
                      {member?.id === userProfile?.id && <span className="host-badge">👑 방장</span>}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 친구 초대 섹션 */}
            <div className="invite-section">
              <h3>📨 친구 초대</h3>
              {friends.length === 0 ? (
                <div className="no-friends">
                  <p>주변에 다른 플레이어가 없습니다.</p>
                  <p className="hint">다른 플레이어가 접속하면 초대할 수 있습니다!</p>
                </div>
              ) : (
                <>
                  <div className="friends-list">
                    {friends.map(friend => {
                      const isInvited = invitedFriends.some(f => f.id === friend.id);
                      const isSelected = selectedFriends.some(f => f.id === friend.id);
                      const isInRoom = roomMembers.some(m => m?.id === friend.id);
                      
                      return (
                        <div 
                          key={friend.id} 
                          className={`friend-card ${isSelected ? 'selected' : ''} ${isInvited ? 'invited' : ''} ${isInRoom ? 'in-room' : ''}`}
                          onClick={() => !isInvited && !isInRoom && toggleFriendSelection(friend)}
                        >
                          <div className="friend-avatar">
                            {friend.selectedProfile ? (
                              <img src={friend.selectedProfile} alt="" />
                            ) : (
                              <span className="default-avatar">👤</span>
                            )}
                            <span className={`status-dot ${friend.isOnline ? 'online' : 'offline'}`} />
                          </div>
                          <div className="friend-info">
                            <span className="friend-name">{friend.username}</span>
                            <span className="friend-status">
                              {isInRoom ? '참여 중' : isInvited ? '초대됨' : friend.isOnline ? '온라인' : '오프라인'}
                            </span>
                          </div>
                          {isSelected && <span className="check-mark">✓</span>}
                        </div>
                      );
                    })}
                  </div>
                  
                  {selectedFriends.length > 0 && (
                    <button className="invite-btn" onClick={handleInviteFriends}>
                      📨 {selectedFriends.length}명 초대하기
                    </button>
                  )}
                </>
              )}
            </div>

            {/* 초대된 친구 대기 중 */}
            {invitedFriends.length > 0 && (
              <div className="invited-section">
                <h4>⏳ 초대 대기 중</h4>
                <div className="invited-list">
                  {invitedFriends.map(friend => (
                    <span key={friend.id} className="invited-tag">
                      {friend.username}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* 방 채팅 (대기실) */}
            {myRoom?.roomId && (
              <div style={{ marginTop: 12 }}>
                <PersonalRoomChat roomId={myRoom.roomId} userProfile={userProfile} />
              </div>
            )}

            {/* 하단 버튼 */}
            <div className="room-actions">
              <button className="leave-btn" onClick={handleLeaveRoom}>
                🚪 방 나가기
              </button>
            </div>
          </div>
        )}

        {/* 공개 룸 찾기 모드 */}
        {!isLoading && currentMode === 'browse' && (
          <div className="personal-room-content">
            <div className="public-rooms-list">
              {availableRooms.length === 0 ? (
                <div className="no-rooms">
                  <span className="no-rooms-icon">🏠</span>
                  <p>주변에 공개된 방이 없습니다</p>
                  <p className="hint">직접 방을 만들어보세요!</p>
                </div>
              ) : (
                availableRooms.map(room => (
                  <div key={room.roomId} className="public-room-card">
                    <div className="room-info">
                      <span className="room-name">{room.roomName}</span>
                      <span className="room-host">👑 {room.hostName}</span>
                    </div>
                    <div className="room-meta">
                      <span className="member-count">
                        👥 {room.members?.length || 1}/{room.maxMembers}
                      </span>
                    </div>
                    <button 
                      className="join-btn"
                      onClick={() => handleJoinPublicRoom(room)}
                      disabled={(room.members?.length || 1) >= room.maxMembers}
                    >
                      입장
                    </button>
                  </div>
                ))
              )}
            </div>

            <div className="mode-switch">
              <button onClick={() => {
                // 방 생성 후 대기실로
                const roomData = {
                  roomId: `room_${Date.now()}`,
                  roomName: `${userProfile?.username || '나'}의 방`,
                  hostId: userProfile?.id,
                  hostName: userProfile?.username,
                  maxMembers: 6,
                  isPrivate: true,
                  members: [userProfile],
                  createdAt: new Date().toISOString()
                };
                setMyRoom(roomData);
                setRoomMembers([userProfile]);
                onCreateRoom?.(roomData);
                setCurrentMode('waiting');
              }}>
                🏠 내 방 만들기
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default PersonalRoomModal;
