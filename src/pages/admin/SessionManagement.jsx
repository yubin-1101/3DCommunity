import React, { useState, useEffect } from 'react';
import './SessionManagement.css';

const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const SessionManagement = () => {
    const [sessions, setSessions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [showConfirmModal, setShowConfirmModal] = useState(false);
    const [selectedSession, setSelectedSession] = useState(null);

    // 세션 목록 조회
    const fetchSessions = async () => {
        try {
            setLoading(true);
            const token = localStorage.getItem('token');
            const response = await fetch(`${API_URL}/api/admin/sessions`, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                },
            });

            if (!response.ok) {
                throw new Error('세션 목록을 불러오는데 실패했습니다.');
            }

            const data = await response.json();
            setSessions(data);
            setError(null);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchSessions();
        // 5초마다 자동 새로고침
        const interval = setInterval(fetchSessions, 5000);
        return () => clearInterval(interval);
    }, []);

    // 강제 로그아웃 확인 모달 열기
    const handleLogoutClick = (session) => {
        setSelectedSession(session);
        setShowConfirmModal(true);
    };

    // 강제 로그아웃 실행
    const handleConfirmLogout = async () => {
        if (!selectedSession) return;

        try {
            const token = localStorage.getItem('token');
            const response = await fetch(`${API_URL}/api/admin/sessions/${selectedSession.userId}`, {
                method: 'DELETE',
                headers: {
                    'Authorization': `Bearer ${token}`,
                },
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || '강제 로그아웃에 실패했습니다.');
            }

            alert(`${selectedSession.username} 사용자가 강제 로그아웃되었습니다.`);
            setShowConfirmModal(false);
            setSelectedSession(null);
            fetchSessions(); // 목록 새로고침
        } catch (err) {
            alert(err.message);
        }
    };

    // 시간 포맷팅
    const formatTime = (dateString) => {
        if (!dateString) return '-';
        const date = new Date(dateString);
        return date.toLocaleString('ko-KR');
    };

    // 접속 시간 계산
    const getConnectionDuration = (connectedAt) => {
        if (!connectedAt) return '-';
        const now = new Date();
        const connected = new Date(connectedAt);
        const diff = Math.floor((now - connected) / 1000); // 초 단위

        if (diff < 60) return `${diff}초`;
        if (diff < 3600) return `${Math.floor(diff / 60)}분`;
        return `${Math.floor(diff / 3600)}시간 ${Math.floor((diff % 3600) / 60)}분`;
    };

    if (loading && sessions.length === 0) {
        return <div className="session-management"><div className="loading">로딩 중...</div></div>;
    }

    return (
        <div className="session-management">
            <div className="page-header">
                <h2>세션 관리</h2>
                <div className="header-actions">
                    <button className="btn-refresh" onClick={fetchSessions}>
                        🔄 새로고침
                    </button>
                    <span className="session-count">
                        현재 접속: <strong>{sessions.length}</strong>명
                    </span>
                </div>
            </div>

            {error && <div className="error-message">{error}</div>}

            <div className="sessions-table-container">
                <table className="sessions-table">
                    <thead>
                        <tr>
                            <th>사용자 ID</th>
                            <th>사용자명</th>
                            <th>세션 ID</th>
                            <th>접속 시간</th>
                            <th>접속 기간</th>
                            <th>작업</th>
                        </tr>
                    </thead>
                    <tbody>
                        {sessions.length === 0 ? (
                            <tr>
                                <td colSpan="6" className="no-data">
                                    현재 접속 중인 사용자가 없습니다.
                                </td>
                            </tr>
                        ) : (
                            sessions.map((session) => (
                                <tr key={session.sessionId}>
                                    <td>{session.userId}</td>
                                    <td>{session.username || 'Unknown'}</td>
                                    <td className="session-id">{session.sessionId.substring(0, 12)}...</td>
                                    <td>{formatTime(session.connectedAt)}</td>
                                    <td>{getConnectionDuration(session.connectedAt)}</td>
                                    <td>
                                        <button
                                            className="btn-logout"
                                            onClick={() => handleLogoutClick(session)}
                                        >
                                            강제 로그아웃
                                        </button>
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>

            {/* 확인 모달 */}
            {showConfirmModal && selectedSession && (
                <div className="modal-overlay" onClick={() => setShowConfirmModal(false)}>
                    <div className="confirm-modal" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h3>강제 로그아웃 확인</h3>
                            <button className="close-button" onClick={() => setShowConfirmModal(false)}>
                                ×
                            </button>
                        </div>
                        <div className="modal-body">
                            <p>
                                정말로 <strong>{selectedSession.username}</strong> 사용자를 강제 로그아웃 하시겠습니까?
                            </p>
                            <p className="warning-text">
                                ⚠️ 해당 사용자의 웹소켓 연결이 즉시 종료됩니다.
                            </p>
                        </div>
                        <div className="modal-actions">
                            <button className="btn-cancel" onClick={() => setShowConfirmModal(false)}>
                                취소
                            </button>
                            <button className="btn-confirm" onClick={handleConfirmLogout}>
                                확인
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default SessionManagement;
