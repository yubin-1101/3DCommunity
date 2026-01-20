import React, { useState, useEffect, useRef } from 'react';
import minigameService from '../../../services/minigameService';
import ProfileAvatar from '../../../components/ProfileAvatar';
import './LiarGame.css';

const DISCUSSION_TIME = 60; // 토론 시간 60초
const VOTE_TIME = 15; // 투표 시간 15초

// 제시어 카테고리
const WORD_CATEGORIES = {
  음식: ['피자', '햄버거', '김치찌개', '초밥', '파스타', '치킨', '라면', '떡볶이', '삼겹살', '불고기'],
  동물: ['강아지', '고양이', '사자', '코끼리', '펭귄', '돌고래', '기린', '호랑이', '판다', '곰'],
  장소: ['학교', '병원', '도서관', '공원', '영화관', '마트', '놀이공원', '해변', '카페', '은행'],
  직업: ['의사', '선생님', '경찰관', '소방관', '요리사', '가수', '배우', '화가', '과학자', '운동선수'],
  물건: ['컴퓨터', '스마트폰', '냉장고', '자동차', '시계', '가방', '우산', '안경', '텔레비전', '카메라']
};

const LiarGame = ({ roomId, isHost, userProfile, players = [], onGameEnd }) => {
  const [gamePhase, setGamePhase] = useState('waiting'); // waiting, reveal, discussion, voting, result
  const [liarId, setLiarId] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('');
  const [isLiar, setIsLiar] = useState(false);
  const [timerSeconds, setTimerSeconds] = useState(0);
  const [votes, setVotes] = useState({}); // { oderId: targetId }
  const [hasVoted, setHasVoted] = useState(false);
  const [voteResult, setVoteResult] = useState(null);
  const [winner, setWinner] = useState(null); // 'liar' or 'citizens'
  const [liarGuess, setLiarGuess] = useState('');
  const [liarGuessResult, setLiarGuessResult] = useState(null);
  const [rematchRequests, setRematchRequests] = useState(new Set());
  const [waitingForRematch, setWaitingForRematch] = useState(false);
  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const chatRef = useRef(null);
  const gameStartedRef = useRef(false);

  // 플레이어 매칭 헬퍼
  const getMyPlayerIndex = () => {
    if (!userProfile || !players || players.length === 0) return -1;
    let index = players.findIndex(p => p.userId === userProfile.id);
    if (index !== -1) return index;
    index = players.findIndex(p => p.userId === userProfile.userId);
    if (index !== -1) return index;
    index = players.findIndex(p => p.username === userProfile.username);
    if (index !== -1) return index;
    return -1;
  };

  // 게임 시작 시 초기화
  useEffect(() => {
    if (!gameStartedRef.current && isHost) {
      gameStartedRef.current = true;
      minigameService.sendGameEvent(roomId, {
        type: 'liarGameStart'
      });
    }
  }, [roomId, isHost]);

  // 채팅 자동 스크롤
  useEffect(() => {
    if (chatRef.current) {
      chatRef.current.scrollTop = chatRef.current.scrollHeight;
    }
  }, [chatMessages]);

  // 게임 이벤트 리스너
  useEffect(() => {
    const handler = (evt) => {
      if (!evt || !evt.type || evt.roomId !== roomId) return;

      switch (evt.type) {
        case 'liarGameStart': {
          // 서버에서 라이어 선정 및 제시어 배포
          setGamePhase('reveal');
          setLiarId(evt.liarId);
          setIsLiar(String(evt.liarId) === String(userProfile.id) ||
                   String(evt.liarId) === String(userProfile.userId));
          setCategory(evt.category);
          setKeyword(evt.isLiar ||
                    (String(evt.liarId) === String(userProfile.id)) ||
                    (String(evt.liarId) === String(userProfile.userId))
                    ? '???' : evt.keyword);
          setVotes({});
          setHasVoted(false);
          setVoteResult(null);
          setWinner(null);
          setLiarGuess('');
          setLiarGuessResult(null);
          setChatMessages([]);

          // 5초 후 토론 시작
          setTimeout(() => {
            setGamePhase('discussion');
            setTimerSeconds(DISCUSSION_TIME);
          }, 5000);
          break;
        }

        case 'liarTimer': {
          setTimerSeconds(parseInt(evt.payload));
          break;
        }

        case 'liarDiscussionEnd': {
          setGamePhase('voting');
          setTimerSeconds(VOTE_TIME);
          break;
        }

        case 'liarVote': {
          setVotes(prev => ({
            ...prev,
            [evt.voterId]: evt.targetId
          }));
          break;
        }

        case 'liarVoteResult': {
          setGamePhase('result');
          setVoteResult(evt.votedPlayer);

          // 라이어가 지목됐는지 확인
          if (String(evt.votedPlayer) === String(evt.actualLiar)) {
            // 라이어 지목 성공 - 라이어에게 정답 맞출 기회
            setWinner('pending');
          } else {
            // 라이어 지목 실패 - 라이어 승리
            setWinner('liar');
            setKeyword(evt.keyword); // 정답 공개
          }
          setLiarId(evt.actualLiar);
          break;
        }

        case 'liarGuess': {
          // 라이어의 정답 추측 결과
          setLiarGuessResult(evt.correct);
          setKeyword(evt.keyword);
          if (evt.correct) {
            setWinner('liar');
          } else {
            setWinner('citizens');
          }
          break;
        }

        case 'liarChat': {
          setChatMessages(prev => [...prev, {
            player: evt.playerName,
            playerId: evt.playerId,
            message: evt.message
          }]);
          break;
        }

        case 'liarRematchRequest': {
          setRematchRequests(prev => new Set([...prev, evt.playerId]));
          break;
        }

        case 'liarRematchStart': {
          setGamePhase('waiting');
          setLiarId(null);
          setKeyword('');
          setCategory('');
          setIsLiar(false);
          setVotes({});
          setHasVoted(false);
          setVoteResult(null);
          setWinner(null);
          setLiarGuess('');
          setLiarGuessResult(null);
          setRematchRequests(new Set());
          setWaitingForRematch(false);
          setChatMessages([]);
          gameStartedRef.current = false;

          // 잠시 후 게임 시작
          setTimeout(() => {
            if (isHost) {
              gameStartedRef.current = true;
              minigameService.sendGameEvent(roomId, {
                type: 'liarGameStart'
              });
            }
          }, 1000);
          break;
        }

        default:
          break;
      }
    };

    minigameService.on('gameEvent', handler);
    return () => minigameService.off('gameEvent', handler);
  }, [roomId, players, userProfile, isHost]);

  // 투표하기
  const handleVote = (targetId) => {
    if (hasVoted || gamePhase !== 'voting') return;

    // 자기 자신에게 투표 불가
    if (String(targetId) === String(userProfile.id) ||
        String(targetId) === String(userProfile.userId)) {
      return;
    }

    minigameService.sendGameEvent(roomId, {
      type: 'liarVote',
      voterId: userProfile.id,
      targetId: targetId,
      voterName: userProfile.username
    });

    setHasVoted(true);
    setVotes(prev => ({
      ...prev,
      [userProfile.id]: targetId
    }));
  };

  // 라이어의 정답 추측
  const handleLiarGuess = () => {
    if (!liarGuess.trim()) return;

    minigameService.sendGameEvent(roomId, {
      type: 'liarGuess',
      guess: liarGuess.trim(),
      playerId: userProfile.id
    });
  };

  // 채팅 전송
  const handleSendChat = () => {
    if (!chatInput.trim() || gamePhase !== 'discussion') return;

    minigameService.sendGameEvent(roomId, {
      type: 'liarChat',
      message: chatInput.trim(),
      playerId: userProfile.id,
      playerName: userProfile.username
    });

    setChatInput('');
  };

  const handleRematchRequest = () => {
    minigameService.sendGameEvent(roomId, {
      type: 'liarRematchRequest',
      playerId: userProfile.id
    });
    setRematchRequests(prev => new Set([...prev, userProfile.id]));
    setWaitingForRematch(true);
  };

  const formatProfileImage = (item) => {
    if (!item) return null;
    if (typeof item === 'object' && item.imagePath) return item;
    if (typeof item === 'string' && item.includes('/')) return { imagePath: item };
    return { imagePath: `/resources/Profile/base-profile${item}.png` };
  };

  const formatOutlineImage = (item) => {
    if (!item) return null;
    if (typeof item === 'object' && item.imagePath) return item;
    if (typeof item === 'string' && item.includes('/')) return { imagePath: item };
    return { imagePath: `/resources/ProfileOutline/base-outline${item}.png` };
  };

  // 투표 집계
  const getVoteCount = (playerId) => {
    return Object.values(votes).filter(v => String(v) === String(playerId)).length;
  };

  const liarPlayer = players.find(p =>
    String(p.userId) === String(liarId)
  );

  const votedPlayer = players.find(p =>
    String(p.userId) === String(voteResult)
  );

  return (
    <div className="liar-game">
      <div className="liar-header">
        <h2>🎭 라이어 게임</h2>
        {(gamePhase === 'discussion' || gamePhase === 'voting') && (
          <div className={`liar-timer ${timerSeconds <= 10 ? 'warning' : ''}`}>
            ⏱️ {timerSeconds}초
          </div>
        )}
      </div>

      {/* 역할 공개 */}
      {gamePhase === 'reveal' && (
        <div className="reveal-phase">
          <div className={`role-card ${isLiar ? 'liar' : 'citizen'}`}>
            <div className="role-icon">{isLiar ? '🎭' : '👤'}</div>
            <h3>{isLiar ? '당신은 라이어입니다!' : '당신은 시민입니다!'}</h3>
            <div className="keyword-reveal">
              <span className="category">카테고리: {category}</span>
              <span className="keyword">
                제시어: <strong>{keyword}</strong>
              </span>
            </div>
            {isLiar && (
              <p className="liar-hint">제시어를 모르지만, 아는 척 해야 합니다!</p>
            )}
          </div>
          <div className="reveal-countdown">잠시 후 토론이 시작됩니다...</div>
        </div>
      )}

      {/* 토론 단계 */}
      {gamePhase === 'discussion' && (
        <div className="discussion-phase">
          <div className="discussion-info">
            <div className="info-item">
              <span className="label">카테고리:</span>
              <span className="value">{category}</span>
            </div>
            <div className="info-item">
              <span className="label">제시어:</span>
              <span className="value keyword">{keyword}</span>
            </div>
          </div>

          <div className="players-grid">
            {players.map((p) => (
              <div key={p.userId} className="player-card">
                <ProfileAvatar
                  profileImage={formatProfileImage(p.selectedProfile)}
                  outlineImage={formatOutlineImage(p.selectedOutline)}
                  size={50}
                />
                <span className="player-name">{p.username}</span>
              </div>
            ))}
          </div>

          <div className="chat-section">
            <div className="chat-messages" ref={chatRef}>
              {chatMessages.length === 0 ? (
                <div className="no-messages">제시어에 대해 토론하세요! 라이어를 찾아내세요!</div>
              ) : (
                chatMessages.map((msg, idx) => (
                  <div key={idx} className={`chat-message ${String(msg.playerId) === String(userProfile.id) || String(msg.playerId) === String(userProfile.userId) ? 'mine' : ''}`}>
                    <span className="sender">{msg.player}:</span>
                    <span className="text">{msg.message}</span>
                  </div>
                ))
              )}
            </div>
            <div className="chat-input-area">
              <input
                type="text"
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSendChat()}
                placeholder="제시어에 대해 이야기하세요..."
              />
              <button onClick={handleSendChat}>전송</button>
            </div>
          </div>
        </div>
      )}

      {/* 투표 단계 */}
      {gamePhase === 'voting' && (
        <div className="voting-phase">
          <h3>🗳️ 라이어를 지목하세요!</h3>
          <p className="vote-instruction">누가 라이어인지 투표하세요. 자신에게는 투표할 수 없습니다.</p>

          <div className="vote-grid">
            {players.map((p) => {
              const isMe = String(p.userId) === String(userProfile.id) ||
                          String(p.userId) === String(userProfile.userId);
              const votedFor = votes[userProfile.id] || votes[userProfile.userId];
              const isSelected = String(votedFor) === String(p.userId);
              const voteCount = getVoteCount(p.userId);

              return (
                <div
                  key={p.userId}
                  className={`vote-card ${isMe ? 'disabled' : ''} ${isSelected ? 'selected' : ''} ${hasVoted ? 'voted' : ''}`}
                  onClick={() => !isMe && !hasVoted && handleVote(p.userId)}
                >
                  <ProfileAvatar
                    profileImage={formatProfileImage(p.selectedProfile)}
                    outlineImage={formatOutlineImage(p.selectedOutline)}
                    size={60}
                  />
                  <span className="player-name">{p.username}</span>
                  {hasVoted && voteCount > 0 && (
                    <span className="vote-count">{voteCount}표</span>
                  )}
                  {isMe && <span className="me-badge">나</span>}
                </div>
              );
            })}
          </div>

          {hasVoted && (
            <div className="vote-status">투표 완료! 다른 플레이어의 투표를 기다리는 중...</div>
          )}
        </div>
      )}

      {/* 결과 단계 */}
      {gamePhase === 'result' && (
        <div className="result-phase">
          <div className="result-content">
            {winner === 'pending' && isLiar && (
              <>
                <h2>😱 당신이 지목되었습니다!</h2>
                <p>마지막 기회! 제시어를 맞추면 승리합니다.</p>
                <div className="guess-section">
                  <input
                    type="text"
                    value={liarGuess}
                    onChange={(e) => setLiarGuess(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && handleLiarGuess()}
                    placeholder="제시어를 입력하세요"
                  />
                  <button onClick={handleLiarGuess}>정답 제출</button>
                </div>
              </>
            )}

            {winner === 'pending' && !isLiar && (
              <>
                <h2>✅ 라이어를 찾았습니다!</h2>
                <p className="voted-player">
                  지목된 플레이어: <strong>{votedPlayer?.username}</strong>
                </p>
                <p>라이어가 제시어를 맞추는 중...</p>
              </>
            )}

            {winner === 'liar' && (
              <>
                <h2>🎭 라이어 승리!</h2>
                <div className="winner-reveal">
                  <p>라이어는 <strong>{liarPlayer?.username}</strong>님이었습니다!</p>
                  <p className="keyword-final">제시어: <strong>{keyword}</strong></p>
                  {liarGuessResult !== null && (
                    <p className="guess-result">
                      {liarGuessResult
                        ? '라이어가 제시어를 맞췄습니다!'
                        : '시민들이 라이어를 찾지 못했습니다!'}
                    </p>
                  )}
                </div>
              </>
            )}

            {winner === 'citizens' && (
              <>
                <h2>👥 시민 승리!</h2>
                <div className="winner-reveal">
                  <p>라이어는 <strong>{liarPlayer?.username}</strong>님이었습니다!</p>
                  <p className="keyword-final">제시어: <strong>{keyword}</strong></p>
                  <p className="guess-result">라이어가 제시어를 맞추지 못했습니다!</p>
                </div>
              </>
            )}

            {(winner === 'liar' || winner === 'citizens') && (
              <div className="result-actions">
                {waitingForRematch ? (
                  <div className="waiting-rematch">⏳ 상대방의 응답 대기 중...</div>
                ) : (
                  <button className="btn-rematch" onClick={handleRematchRequest}>
                    다시 하기
                  </button>
                )}
                <button className="btn-back" onClick={onGameEnd}>
                  대기방으로
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 대기 화면 */}
      {gamePhase === 'waiting' && (
        <div className="waiting-phase">
          <div className="waiting-icon">🎭</div>
          <h3>게임 준비 중...</h3>
        </div>
      )}
    </div>
  );
};

export default LiarGame;
