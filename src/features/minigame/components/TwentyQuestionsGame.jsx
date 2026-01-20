import React, { useState, useEffect, useRef } from 'react';
import minigameService from '../../../services/minigameService';
import ProfileAvatar from '../../../components/ProfileAvatar';
import './TwentyQuestionsGame.css';

const MAX_QUESTIONS = 20;

// 미리 정의된 제시어 목록
const WORD_CATEGORIES = {
  동물: ['강아지', '고양이', '코끼리', '사자', '기린', '펭귄', '돌고래', '독수리', '토끼', '곰'],
  음식: ['피자', '햄버거', '김치찌개', '초밥', '파스타', '치킨', '라면', '떡볶이', '비빔밥', '삼겹살'],
  물건: ['컴퓨터', '스마트폰', '냉장고', '자동차', '텔레비전', '시계', '가방', '우산', '안경', '의자'],
  장소: ['학교', '병원', '도서관', '공원', '영화관', '마트', '놀이공원', '해변', '산', '카페'],
  인물: ['의사', '선생님', '경찰관', '소방관', '요리사', '가수', '운동선수', '과학자', '화가', '배우']
};

const TwentyQuestionsGame = ({ roomId, isHost, userProfile, players = [], onGameEnd }) => {
  const [gamePhase, setGamePhase] = useState('selecting'); // selecting, questioning, guessing, ended
  const [questioner, setQuestioner] = useState(null); // 출제자 (첫 번째 플레이어)
  const [answer, setAnswer] = useState(''); // 정답 (출제자만 알고 있음)
  const [category, setCategory] = useState('');
  const [questionHistory, setQuestionHistory] = useState([]);
  const [currentQuestion, setCurrentQuestion] = useState('');
  const [questionCount, setQuestionCount] = useState(0);
  const [currentGuesserIndex, setCurrentGuesserIndex] = useState(1);
  const [guessInput, setGuessInput] = useState('');
  const [winner, setWinner] = useState(null);
  const [rematchRequests, setRematchRequests] = useState(new Set());
  const [waitingForRematch, setWaitingForRematch] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedWord, setSelectedWord] = useState('');
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
    return -1;
  };

  const myPlayerIndex = getMyPlayerIndex();
  const isQuestioner = myPlayerIndex === 0;
  const guessers = players.filter((_, idx) => idx !== 0);
  const isMyTurnToAsk = !isQuestioner && (myPlayerIndex === currentGuesserIndex);

  // 게임 시작 시 초기화
  useEffect(() => {
    if (!gameStartedRef.current && isHost) {
      gameStartedRef.current = true;
      minigameService.sendGameEvent(roomId, {
        type: 'twentyQStart'
      });
    }
  }, [roomId, isHost]);

  // 게임 이벤트 리스너
  useEffect(() => {
    const handler = (evt) => {
      if (!evt || !evt.type || evt.roomId !== roomId) return;

      switch (evt.type) {
        case 'twentyQStart': {
          setGamePhase('selecting');
          setQuestioner(players[0]);
          setQuestionHistory([]);
          setQuestionCount(0);
          setCurrentGuesserIndex(1);
          setWinner(null);
          break;
        }

        case 'twentyQWordSelected': {
          setAnswer(evt.isQuestioner ? evt.word : '???');
          setCategory(evt.category);
          setGamePhase('questioning');
          break;
        }

        case 'twentyQQuestion': {
          setQuestionHistory(prev => [...prev, {
            type: 'question',
            player: evt.playerName,
            content: evt.question
          }]);
          setQuestionCount(prev => prev + 1);
          setCurrentQuestion(evt.question);
          break;
        }

        case 'twentyQAnswer': {
          setQuestionHistory(prev => [...prev, {
            type: 'answer',
            content: evt.answer // 예/아니오
          }]);
          // 다음 질문자로
          setCurrentGuesserIndex(prev => {
            const nextIdx = prev + 1;
            return nextIdx >= players.length ? 1 : nextIdx;
          });
          setCurrentQuestion('');
          break;
        }

        case 'twentyQGuess': {
          setQuestionHistory(prev => [...prev, {
            type: 'guess',
            player: evt.playerName,
            content: evt.guess,
            correct: evt.correct
          }]);

          if (evt.correct) {
            setGamePhase('ended');
            setWinner(evt.playerId);
            setAnswer(evt.actualAnswer);
          } else {
            // 다음 질문자로
            setCurrentGuesserIndex(prev => {
              const nextIdx = prev + 1;
              return nextIdx >= players.length ? 1 : nextIdx;
            });
          }
          break;
        }

        case 'twentyQEnd': {
          setGamePhase('ended');
          setWinner(evt.winnerId);
          setAnswer(evt.answer);
          break;
        }

        case 'twentyQRematchRequest': {
          setRematchRequests(prev => new Set([...prev, evt.playerId]));
          break;
        }

        case 'twentyQRematchStart': {
          setGamePhase('selecting');
          setQuestioner(players[0]);
          setAnswer('');
          setCategory('');
          setQuestionHistory([]);
          setQuestionCount(0);
          setCurrentGuesserIndex(1);
          setWinner(null);
          setRematchRequests(new Set());
          setWaitingForRematch(false);
          setSelectedCategory('');
          setSelectedWord('');
          gameStartedRef.current = true;
          break;
        }

        default:
          break;
      }
    };

    minigameService.on('gameEvent', handler);
    return () => minigameService.off('gameEvent', handler);
  }, [roomId, players, userProfile]);

  // 카테고리 선택 (출제자)
  const handleCategorySelect = (cat) => {
    setSelectedCategory(cat);
    setSelectedWord('');
  };

  // 단어 선택 및 게임 시작 (출제자)
  const handleWordSelect = (word) => {
    setSelectedWord(word);
  };

  const handleConfirmWord = () => {
    if (!selectedWord) return;

    minigameService.sendGameEvent(roomId, {
      type: 'twentyQWordSelected',
      word: selectedWord,
      category: selectedCategory,
      questionerId: userProfile.id
    });
  };

  // 질문 제출
  const handleAskQuestion = () => {
    if (!currentQuestion.trim()) return;

    minigameService.sendGameEvent(roomId, {
      type: 'twentyQQuestion',
      question: currentQuestion.trim(),
      playerId: userProfile.id,
      playerName: userProfile.username
    });

    setCurrentQuestion('');
  };

  // 예/아니오 답변 (출제자)
  const handleAnswer = (ans) => {
    minigameService.sendGameEvent(roomId, {
      type: 'twentyQAnswer',
      answer: ans,
      questionerId: userProfile.id
    });
  };

  // 정답 추측
  const handleGuess = () => {
    if (!guessInput.trim()) return;

    minigameService.sendGameEvent(roomId, {
      type: 'twentyQGuess',
      guess: guessInput.trim(),
      playerId: userProfile.id,
      playerName: userProfile.username
    });

    setGuessInput('');
  };

  // 20문제 초과시 출제자 승리 선언
  const handleGiveUp = () => {
    minigameService.sendGameEvent(roomId, {
      type: 'twentyQEnd',
      winnerId: players[0].userId,
      answer: answer
    });
  };

  const handleRematchRequest = () => {
    minigameService.sendGameEvent(roomId, {
      type: 'twentyQRematchRequest',
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

  const winnerPlayer = players.find(p => p.userId === winner);
  const currentGuesser = players[currentGuesserIndex];
  const lastQuestion = questionHistory.filter(q => q.type === 'question').slice(-1)[0];

  return (
    <div className="twenty-q-game">
      <div className="twenty-q-header">
        <h2>❓ 스무고개</h2>
        <div className="twenty-q-info">
          <div className="questioner-info">
            <span className="label">출제자:</span>
            <span className="name">{players[0]?.username}</span>
          </div>
          {gamePhase === 'questioning' && (
            <>
              <div className="category-info">
                <span className="label">카테고리:</span>
                <span className="value">{category}</span>
              </div>
              <div className="question-count">
                <span className="label">질문:</span>
                <span className={`count ${questionCount >= 15 ? 'warning' : ''}`}>
                  {questionCount}/{MAX_QUESTIONS}
                </span>
              </div>
            </>
          )}
        </div>
      </div>

      {/* 단어 선택 화면 (출제자용) */}
      {gamePhase === 'selecting' && isQuestioner && (
        <div className="word-selection">
          <h3>🎯 제시어를 선택하세요</h3>
          <div className="category-buttons">
            {Object.keys(WORD_CATEGORIES).map(cat => (
              <button
                key={cat}
                className={`category-btn ${selectedCategory === cat ? 'selected' : ''}`}
                onClick={() => handleCategorySelect(cat)}
              >
                {cat}
              </button>
            ))}
          </div>
          {selectedCategory && (
            <div className="word-grid">
              {WORD_CATEGORIES[selectedCategory].map(word => (
                <button
                  key={word}
                  className={`word-btn ${selectedWord === word ? 'selected' : ''}`}
                  onClick={() => handleWordSelect(word)}
                >
                  {word}
                </button>
              ))}
            </div>
          )}
          {selectedWord && (
            <button className="confirm-btn" onClick={handleConfirmWord}>
              '{selectedWord}' 선택 확정
            </button>
          )}
        </div>
      )}

      {/* 대기 화면 (추측자용) */}
      {gamePhase === 'selecting' && !isQuestioner && (
        <div className="waiting-selection">
          <div className="waiting-icon">⏳</div>
          <h3>{players[0]?.username}님이 제시어를 선택하고 있습니다...</h3>
        </div>
      )}

      {/* 질문 단계 */}
      {gamePhase === 'questioning' && (
        <div className="questioning-phase">
          {/* 질문 기록 */}
          <div className="question-history">
            {questionHistory.length === 0 ? (
              <div className="no-questions">아직 질문이 없습니다. 질문을 시작하세요!</div>
            ) : (
              questionHistory.map((item, idx) => (
                <div key={idx} className={`history-item ${item.type}`}>
                  {item.type === 'question' && (
                    <>
                      <span className="question-number">Q{Math.ceil((idx + 1) / 2)}</span>
                      <span className="player">{item.player}:</span>
                      <span className="content">{item.content}</span>
                    </>
                  )}
                  {item.type === 'answer' && (
                    <span className={`answer-badge ${item.content === '예' ? 'yes' : 'no'}`}>
                      {item.content}
                    </span>
                  )}
                  {item.type === 'guess' && (
                    <>
                      <span className="guess-icon">🎯</span>
                      <span className="player">{item.player}:</span>
                      <span className="content">"{item.content}"</span>
                      <span className={`result ${item.correct ? 'correct' : 'wrong'}`}>
                        {item.correct ? '정답!' : '오답'}
                      </span>
                    </>
                  )}
                </div>
              ))
            )}
          </div>

          {/* 출제자: 질문에 답변 */}
          {isQuestioner && lastQuestion && !questionHistory.some(q => q.type === 'answer' && questionHistory.indexOf(q) > questionHistory.lastIndexOf(lastQuestion)) && (
            <div className="answer-section">
              <div className="current-question">
                <span className="label">질문:</span>
                <span className="question">{lastQuestion.content}</span>
              </div>
              <div className="answer-buttons">
                <button className="answer-btn yes" onClick={() => handleAnswer('예')}>
                  ⭕ 예
                </button>
                <button className="answer-btn no" onClick={() => handleAnswer('아니오')}>
                  ❌ 아니오
                </button>
              </div>
              <div className="answer-hint">정답: {answer}</div>
            </div>
          )}

          {/* 추측자: 질문 입력 */}
          {!isQuestioner && isMyTurnToAsk && (
            <div className="question-input-section">
              <div className="turn-notice">🎮 당신의 차례입니다!</div>
              <div className="input-group">
                <input
                  ref={inputRef}
                  type="text"
                  value={currentQuestion}
                  onChange={(e) => setCurrentQuestion(e.target.value)}
                  onKeyPress={(e) => e.key === 'Enter' && handleAskQuestion()}
                  placeholder="예/아니오로 답할 수 있는 질문을 하세요"
                />
                <button onClick={handleAskQuestion} disabled={!currentQuestion.trim()}>
                  질문하기
                </button>
              </div>
              <div className="guess-section">
                <input
                  type="text"
                  value={guessInput}
                  onChange={(e) => setGuessInput(e.target.value)}
                  onKeyPress={(e) => e.key === 'Enter' && handleGuess()}
                  placeholder="정답을 알겠다면 입력하세요"
                />
                <button onClick={handleGuess} disabled={!guessInput.trim()} className="guess-btn">
                  정답 맞추기
                </button>
              </div>
            </div>
          )}

          {/* 다른 추측자 차례 대기 */}
          {!isQuestioner && !isMyTurnToAsk && (
            <div className="waiting-turn">
              ⏳ {currentGuesser?.username}님의 차례입니다...
            </div>
          )}

          {/* 출제자: 질문 대기 */}
          {isQuestioner && (!lastQuestion || questionHistory.some(q => q.type === 'answer' && questionHistory.indexOf(q) > questionHistory.lastIndexOf(lastQuestion))) && (
            <div className="waiting-question">
              <div className="answer-hint">정답: {answer}</div>
              <div className="waiting-text">⏳ {currentGuesser?.username}님이 질문을 생각하고 있습니다...</div>
            </div>
          )}

          {/* 20문제 초과시 */}
          {questionCount >= MAX_QUESTIONS && isQuestioner && (
            <div className="game-over-option">
              <p>20개의 질문이 모두 소진되었습니다!</p>
              <button onClick={handleGiveUp}>게임 종료 (출제자 승리)</button>
            </div>
          )}
        </div>
      )}

      {/* 게임 종료 */}
      {gamePhase === 'ended' && (
        <div className="twenty-q-result">
          <div className="result-content">
            <h2>🎉 게임 종료!</h2>
            <div className="answer-reveal">
              <span className="label">정답은:</span>
              <span className="answer">{answer}</span>
            </div>
            {winnerPlayer && (
              <p className="winner-info">
                <span className="winner-icon">👑</span>
                {winnerPlayer.username}님 승리!
              </p>
            )}
            <p className="question-summary">
              총 {questionCount}개의 질문이 사용되었습니다.
            </p>
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

export default TwentyQuestionsGame;
