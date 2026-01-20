import React, { useState, useEffect, useRef } from 'react';
import minigameService from '../../../services/minigameService';
import ProfileAvatar from '../../../components/ProfileAvatar';
import './WordChainGame.css';

const TURN_TIME = 10; // 턴당 10초

const WordChainGame = ({ roomId, isHost, userProfile, players = [], onGameEnd }) => {
  const [currentWord, setCurrentWord] = useState('');
  const [wordHistory, setWordHistory] = useState([]);
  const [inputWord, setInputWord] = useState('');
  const [currentTurnIndex, setCurrentTurnIndex] = useState(0);
  const [timerSeconds, setTimerSeconds] = useState(TURN_TIME);
  const [gameStatus, setGameStatus] = useState('playing'); // playing, ended
  const [winner, setWinner] = useState(null);
  const [loser, setLoser] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [rematchRequests, setRematchRequests] = useState(new Set());
  const [waitingForRematch, setWaitingForRematch] = useState(false);
  const inputRef = useRef(null);
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
    index = players.findIndex(p => String(p.userId) === String(userProfile.id));
    if (index !== -1) return index;
    return -1;
  };

  const myPlayerIndex = getMyPlayerIndex();
  const isMyTurn = currentTurnIndex === myPlayerIndex;

  // 게임 시작 시 초기화
  useEffect(() => {
    if (!gameStartedRef.current && isHost) {
      gameStartedRef.current = true;
      minigameService.sendGameEvent(roomId, {
        type: 'wordChainStart'
      });
    }
  }, [roomId, isHost]);

  // 내 턴이면 입력창 포커스
  useEffect(() => {
    if (isMyTurn && inputRef.current && gameStatus === 'playing') {
      inputRef.current.focus();
    }
  }, [isMyTurn, gameStatus]);

  // 게임 이벤트 리스너
  useEffect(() => {
    const handler = (evt) => {
      if (!evt || !evt.type || evt.roomId !== roomId) return;

      switch (evt.type) {
        case 'wordChainStart': {
          // 게임 시작 - 첫 단어 설정
          const startWords = ['사과', '바나나', '컴퓨터', '학교', '음악', '여행', '게임', '친구'];
          const randomWord = startWords[Math.floor(Math.random() * startWords.length)];
          setCurrentWord(randomWord);
          setWordHistory([{ word: randomWord, player: '시스템', isStart: true }]);
          setCurrentTurnIndex(0);
          setTimerSeconds(TURN_TIME);
          setGameStatus('playing');
          break;
        }

        case 'wordChainWord': {
          // 단어 입력됨
          setCurrentWord(evt.word);
          setWordHistory(prev => [...prev, {
            word: evt.word,
            player: evt.playerName,
            playerId: evt.playerId
          }]);
          setCurrentTurnIndex(evt.nextTurnIndex);
          setTimerSeconds(TURN_TIME);
          setErrorMessage('');
          break;
        }

        case 'wordChainTimer': {
          setTimerSeconds(parseInt(evt.payload));
          break;
        }

        case 'wordChainError': {
          // 오류 메시지 (잘못된 단어 등)
          if (evt.playerId === userProfile.id || evt.playerId === userProfile.userId) {
            setErrorMessage(evt.message);
          }
          break;
        }

        case 'wordChainEnd': {
          // 게임 종료
          setGameStatus('ended');
          setWinner(evt.winnerId);
          setLoser(evt.loserId);
          break;
        }

        case 'wordChainRematchRequest': {
          setRematchRequests(prev => new Set([...prev, evt.playerId]));
          break;
        }

        case 'wordChainRematchStart': {
          // 게임 재시작
          const startWords = ['사과', '바나나', '컴퓨터', '학교', '음악', '여행', '게임', '친구'];
          const randomWord = startWords[Math.floor(Math.random() * startWords.length)];
          setCurrentWord(randomWord);
          setWordHistory([{ word: randomWord, player: '시스템', isStart: true }]);
          setCurrentTurnIndex(0);
          setTimerSeconds(TURN_TIME);
          setGameStatus('playing');
          setWinner(null);
          setLoser(null);
          setRematchRequests(new Set());
          setWaitingForRematch(false);
          setErrorMessage('');
          setInputWord('');
          break;
        }

        default:
          break;
      }
    };

    minigameService.on('gameEvent', handler);
    return () => minigameService.off('gameEvent', handler);
  }, [roomId, userProfile, players]);

  // 한글 끝 글자 추출
  const getLastChar = (word) => {
    if (!word) return '';
    const lastChar = word.charAt(word.length - 1);

    // 두음법칙 처리 (선택사항)
    const dueum = {
      '녀': '여', '뇨': '요', '뉴': '유', '니': '이',
      '랴': '야', '려': '여', '례': '예', '료': '요',
      '류': '유', '리': '이', '라': '나', '래': '내',
      '로': '노', '뢰': '뇌', '루': '누', '르': '느'
    };

    return dueum[lastChar] || lastChar;
  };

  // 단어 제출
  const handleSubmit = () => {
    if (!isMyTurn || gameStatus !== 'playing') return;

    const word = inputWord.trim();
    if (!word) return;

    // 클라이언트 측 검증
    const lastChar = getLastChar(currentWord);
    const firstChar = word.charAt(0);

    // 끝말잇기 규칙 검증
    if (lastChar !== firstChar) {
      setErrorMessage(`'${lastChar}'로 시작하는 단어를 입력하세요!`);
      return;
    }

    // 이미 사용된 단어 체크
    if (wordHistory.some(h => h.word === word)) {
      setErrorMessage('이미 사용된 단어입니다!');
      return;
    }

    // 한 글자 금지
    if (word.length < 2) {
      setErrorMessage('두 글자 이상 입력하세요!');
      return;
    }

    // 서버에 전송
    minigameService.sendGameEvent(roomId, {
      type: 'wordChainWord',
      word: word,
      playerId: userProfile.id,
      playerName: userProfile.username,
      nextTurnIndex: (currentTurnIndex + 1) % players.length
    });

    setInputWord('');
    setErrorMessage('');
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSubmit();
    }
  };

  const handleRematchRequest = () => {
    minigameService.sendGameEvent(roomId, {
      type: 'wordChainRematchRequest',
      playerId: userProfile.id,
      playerName: userProfile.username
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

  const currentPlayer = players[currentTurnIndex];
  const winnerPlayer = players.find(p => p.userId === winner);
  const loserPlayer = players.find(p => p.userId === loser);

  return (
    <div className="word-chain-game">
      <div className="word-chain-header">
        <h2>🔤 끝말잇기</h2>
        <div className="word-chain-players">
          {players.map((p, idx) => (
            <div
              key={p.userId}
              className={`word-chain-player ${idx === currentTurnIndex ? 'active' : ''}`}
            >
              <ProfileAvatar
                profileImage={formatProfileImage(p.selectedProfile)}
                outlineImage={formatOutlineImage(p.selectedOutline)}
                size={40}
              />
              <span className="player-name">{p.username}</span>
            </div>
          ))}
        </div>
      </div>

      {gameStatus === 'playing' && (
        <>
          <div className="word-chain-current">
            <div className="current-word-label">현재 단어</div>
            <div className="current-word">{currentWord}</div>
            <div className="next-char">
              다음 글자: <span className="highlight">{getLastChar(currentWord)}</span>
            </div>
          </div>

          <div className="word-chain-turn-info">
            <div className={`turn-indicator ${isMyTurn ? 'my-turn' : ''}`}>
              {isMyTurn ? '🎮 당신의 차례!' : `⏳ ${currentPlayer?.username}님의 차례`}
            </div>
            <div className={`timer ${timerSeconds <= 3 ? 'warning' : ''}`}>
              ⏱️ {timerSeconds}초
            </div>
          </div>

          <div className="word-chain-input-area">
            <input
              ref={inputRef}
              type="text"
              value={inputWord}
              onChange={(e) => setInputWord(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder={isMyTurn ? `'${getLastChar(currentWord)}'로 시작하는 단어 입력` : '상대방 차례입니다...'}
              disabled={!isMyTurn}
              className={!isMyTurn ? 'disabled' : ''}
            />
            <button
              onClick={handleSubmit}
              disabled={!isMyTurn || !inputWord.trim()}
              className="submit-btn"
            >
              입력
            </button>
          </div>

          {errorMessage && (
            <div className="error-message">{errorMessage}</div>
          )}

          <div className="word-history">
            <h4>단어 기록</h4>
            <div className="history-list">
              {wordHistory.map((h, idx) => (
                <span
                  key={idx}
                  className={`history-word ${h.isStart ? 'start' : ''}`}
                >
                  {h.word}
                  {idx < wordHistory.length - 1 && ' → '}
                </span>
              ))}
            </div>
          </div>
        </>
      )}

      {gameStatus === 'ended' && (
        <div className="word-chain-result">
          <div className="result-content">
            <h2>🎉 게임 종료!</h2>
            {loserPlayer && (
              <p className="loser-info">
                <span className="loser-icon">💀</span>
                {loserPlayer.username}님이 탈락했습니다!
              </p>
            )}
            {winnerPlayer && (
              <p className="winner-info">
                <span className="winner-icon">👑</span>
                {winnerPlayer.username}님 승리!
              </p>
            )}
            <div className="final-history">
              <h4>최종 단어 기록 ({wordHistory.length - 1}개)</h4>
              <div className="history-list">
                {wordHistory.map((h, idx) => (
                  <span key={idx} className="history-word">
                    {h.word}
                    {idx < wordHistory.length - 1 && ' → '}
                  </span>
                ))}
              </div>
            </div>
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
          </div>
        </div>
      )}
    </div>
  );
};

export default WordChainGame;
