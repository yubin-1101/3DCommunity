import React, { useState, useEffect, useCallback } from 'react';
import minigameService from '../../../services/minigameService';
import './AimingGame.css';

const GAME_CONFIG = {
  GAME_AREA_WIDTH: 500,
  GAME_AREA_HEIGHT: 400,
};

const SimplePlayerList = ({ scores, players, userProfile }) => (
  <div className="aim-game-scoreboard">
    <div className="scoreboard-title">🎯 실시간 스코어</div>
    <div className="scoreboard-players">
      {players.map(p => {
        const isSelf = p.userId === userProfile?.id;
        const score = scores[p.userId] || 0;
        return (
          <div key={p.userId} className={`scoreboard-player ${isSelf ? 'current-player' : ''}`}>
            <div className="player-info">
              <span className="player-rank">#{players.map(pl => scores[pl.userId] || 0).sort((a, b) => b - a).indexOf(score) + 1}</span>
              <span className="player-name">{p.username}</span>
              {isSelf && <span className="you-badge">YOU</span>}
            </div>
            <div className="player-score-display">
              <span className="score-number">{score}</span>
              <span className="score-label">점</span>
            </div>
          </div>
        );
      })}
    </div>
  </div>
);

export default function AimingGame({ roomId, isHost, userProfile, players = [], onGameEnd }) {
  console.log('AimingGame mounted');
  const [targets, setTargets] = useState({}); // Using an object for quick lookup by ID
  const [scores, setScores] = useState({});
  const [gameStatus, setGameStatus] = useState('playing'); // playing, ended
  const [finalScores, setFinalScores] = useState(null);
  const [remainingTime, setRemainingTime] = useState(30); // 제한 시간

  // Initialize scores for all players
  useEffect(() => {
    const initialScores = {};
    players.forEach(p => {
      initialScores[p.userId] = 0;
    });
    setScores(initialScores);
    return () => console.log('AimingGame unmounted');
  }, [players]);

  // Main event handler for backend communication
  useEffect(() => {
    const handler = (evt) => {
      if (!evt || !evt.type || evt.roomId !== roomId) return;

      console.log('AimGame received event:', evt); // Uncommented log

      switch (evt.type) {
        case 'spawnTarget':
          setTargets(prev => {
            const newTargets = {
              ...prev,
              [evt.target.id]: evt.target,
            };
            console.log('Targets after spawn:', newTargets); // Added log
            return newTargets;
          });
          break;

        case 'targetRemoved':
          setTargets(prev => {
            const newTargets = { ...prev };
            delete newTargets[evt.target.id];
            console.log('Targets after removal:', newTargets); // Added log
            return newTargets;
          });
          break;

        case 'scoreUpdate':
          setScores(prev => ({
            ...prev,
            [evt.playerId]: parseInt(evt.payload, 10),
          }));
          break;

        case 'aimTimer':
          setRemainingTime(parseInt(evt.payload, 10));
          break;

        case 'gameEnd':
          setGameStatus('ended');
          setFinalScores(evt.payload);
          setTargets({}); // Clear all targets
          console.log('Game ended. Final scores:', evt.payload); // Added log
          break;

        default:
          break;
      }
    };

    minigameService.on('gameEvent', handler);

    // Mount 시 현재 게임 상태 요청 (중간 입장/새로고침 대비)
    minigameService.requestGameState(roomId);

    return () => minigameService.off('gameEvent', handler);
  }, [roomId]);

  const handleTargetClick = (targetId) => {
    if (gameStatus !== 'playing') return;

    // Send hit event to the backend. The backend is the source of truth.
    minigameService.handleHit(
      roomId,
      userProfile.id,
      userProfile.username,
      targetId,
      Date.now()
    );
  };

  console.log('Rendering targets:', Object.values(targets).length, targets); // Added log

  return (
    <div className="aim-game-overlay">
      <div className="aim-game-container">
        <div className="aim-game-header">
          <div className="aim-game-timer">
            <div className="timer-icon">⏱️</div>
            <div className="timer-value">{remainingTime}s</div>
          </div>
          <SimplePlayerList scores={scores} players={players} userProfile={userProfile} />
        </div>
        <div
          className="aim-game-area"
          ref={(el) => {
            if (el && Object.values(targets).length > 0) {
              const rect = el.getBoundingClientRect();
              // Target positions will be calculated dynamically
            }
          }}
        >
          {gameStatus === 'playing' && Object.values(targets).map(target => {
            const areaEl = document.querySelector('.aim-game-area');
            const areaWidth = areaEl?.clientWidth || 500;
            const areaHeight = areaEl?.clientHeight || 400;
            const targetSize = Math.min(areaWidth, areaHeight) * 0.08; // 동적 크기
            
            return (
              <div
                key={target.id}
                className="aim-game-target"
                style={{
                  left: `${target.x * (areaWidth - targetSize)}px`,
                  top: `${target.y * (areaHeight - targetSize)}px`,
                  width: `${targetSize}px`,
                  height: `${targetSize}px`,
                }}
                onClick={() => handleTargetClick(target.id)}
              />
            );
          })}

          {gameStatus === 'ended' && (
            <div className="aim-game-over-screen">
              <div className="game-over-content">
                <div className="trophy-icon">🏆</div>
                <h2 className="game-over-title">게임 종료!</h2>
                <p className="game-over-subtitle">최종 결과</p>
                
                <div className="final-rankings">
                  {players
                    .map(p => ({ ...p, score: scores[p.userId] || 0 }))
                    .sort((a, b) => b.score - a.score)
                    .map((p, index) => {
                      const isSelf = p.userId === userProfile?.id;
                      const medals = ['🥇', '🥈', '🥉'];
                      return (
                        <div key={p.userId} className={`final-rank-item ${isSelf ? 'highlight' : ''}`}>
                          <div className="rank-medal">
                            {index < 3 ? medals[index] : `${index + 1}위`}
                          </div>
                          <div className="rank-player-info">
                            <span className="rank-player-name">{p.username}</span>
                            {isSelf && <span className="rank-you-badge">YOU</span>}
                          </div>
                          <div className="rank-score">{p.score}점</div>
                        </div>
                      );
                    })}
                </div>

                <button className="back-to-lobby-btn" onClick={onGameEnd}>
                  <span className="btn-icon">🚪</span>
                  대기방으로 돌아가기
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
