import React, { useState, useEffect } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import './AdminLayout.css';
import { jwtDecode } from 'jwt-decode';

const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const AdminLayout = () => {
  const navigate = useNavigate();
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [adminInfo, setAdminInfo] = useState({ nickname: '관리자', email: '' });

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      try {
        const decoded = jwtDecode(token);
        setAdminInfo({
          nickname: decoded.nickname || decoded.sub || '관리자',
          email: decoded.sub || '',
        });
      } catch (error) {
        console.error('토큰 디코딩 실패:', error);
      }
    }
  }, []);

  const menuItems = [
    { path: '/admin', label: '대시보드', icon: '📊' },
    { path: '/admin/users', label: '사용자 관리', icon: '👥' },
    { path: '/admin/sessions', label: '세션 관리', icon: '🔑' },
    { path: '/admin/reports', label: '신고 관리', icon: '🚨' },
    { path: '/admin/notices', label: '공지사항', icon: '📢' },
    { path: '/admin/chat-logs', label: '채팅 로그', icon: '💬' },
    { path: '/admin/boards', label: '게시판 관리', icon: '📝' },
    { path: '/admin/rooms', label: '게임 방 관리', icon: '🎮' },
    { path: '/admin/shop', label: '상점 관리', icon: '🛒' },
    { path: '/admin/payments', label: '결제/환불', icon: '💳' },
    { path: '/admin/statistics', label: '통계', icon: '📈' },
    { path: '/admin/audit-logs', label: '감사 로그', icon: '📋' },
    { path: '/admin/profile-items', label: '프로필 아이템', icon: '🎨' },
    { path: '/admin/system', label: '시스템', icon: '⚙️' },
  ];

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/');
  };

  const handleUpdateProfile = async (nickname) => {
    try {
      const token = localStorage.getItem('token');
      const response = await fetch(`${API_URL}/api/profile/update`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ nickname }),
      });

      if (response.ok) {
        setAdminInfo({ ...adminInfo, nickname });
        alert('프로필이 업데이트되었습니다.');
        return true;
      } else {
        alert('프로필 업데이트에 실패했습니다.');
        return false;
      }
    } catch (error) {
      console.error('프로필 업데이트 오류:', error);
      alert('오류가 발생했습니다.');
      return false;
    }
  };

  return (
    <div className="admin-layout">
      <aside className={`admin-sidebar ${isSidebarOpen ? 'open' : 'closed'}`}>
        <div className="sidebar-header">
          <h2>MetaPlaza Admin</h2>
          <button
            className="sidebar-toggle"
            onClick={() => setIsSidebarOpen(!isSidebarOpen)}
          >
            {isSidebarOpen ? '◀' : '▶'}
          </button>
        </div>

        <nav className="sidebar-nav">
          {menuItems.map((item) => (
            <button
              key={item.path}
              className="nav-item"
              onClick={() => navigate(item.path)}
            >
              <span className="nav-icon">{item.icon}</span>
              {isSidebarOpen && <span className="nav-label">{item.label}</span>}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <button className="logout-btn" onClick={handleLogout}>
            <span className="nav-icon">🚪</span>
            {isSidebarOpen && <span>로그아웃</span>}
          </button>
        </div>
      </aside>

      <main className={`admin-main ${isSidebarOpen ? 'sidebar-open' : 'sidebar-closed'}`}>
        <header className="admin-header">
          <h1>관리자 페이지</h1>
          <div className="admin-user-info">
            <button
              className="profile-button"
              onClick={() => setShowProfileModal(true)}
              title="프로필 수정"
            >
              <span className="profile-icon">👤</span>
              <span className="profile-name">{adminInfo.nickname}</span>
            </button>
          </div>
        </header>

        <div className="admin-content">
          <Outlet />
        </div>

        {showProfileModal && (
          <AdminProfileModal
            adminInfo={adminInfo}
            onClose={() => setShowProfileModal(false)}
            onUpdate={handleUpdateProfile}
          />
        )}
      </main>
    </div>
  );
};

const AdminProfileModal = ({ adminInfo, onClose, onUpdate }) => {
  const [nickname, setNickname] = useState(adminInfo.nickname);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsLoading(true);
    const success = await onUpdate(nickname);
    setIsLoading(false);
    if (success) {
      onClose();
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="admin-profile-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>프로필 수정</h2>
          <button className="close-button" onClick={onClose}>×</button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>이메일</label>
            <input type="text" value={adminInfo.email} disabled />
          </div>
          <div className="form-group">
            <label>닉네임</label>
            <input
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              required
              maxLength={20}
            />
          </div>
          <div className="modal-actions">
            <button type="submit" className="btn-save" disabled={isLoading}>
              {isLoading ? '저장 중...' : '저장'}
            </button>
            <button type="button" className="btn-cancel" onClick={onClose}>
              취소
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AdminLayout;
