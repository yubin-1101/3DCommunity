import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import App from './App';
import AdminLayout from './pages/admin/AdminLayout';
import Dashboard from './pages/admin/Dashboard';
import UserManagement from './pages/admin/UserManagement';
import ProfileItemManager from './pages/admin/ProfileItemManager';
import NoticeManagement from './pages/admin/NoticeManagement';
import BoardManagement from './pages/admin/BoardManagement';
import ReportManagement from './pages/admin/ReportManagement';
import ChatLogManagement from './pages/admin/ChatLogManagement';
import PaymentManagement from './pages/admin/PaymentManagement';
import { ShopManagement } from './features/shop';
import ProtectedRoute from './components/ProtectedRoute';
import MinigameSelectPage from './pages/MinigameSelectPage';
import MapGamePageNew from './pages/MapGamePageNew';
import PaymentCheckout from './features/payment/components/PaymentCheckout';
import PaymentCallback from './features/payment/components/PaymentCallback';
import Statistics from './pages/admin/Statistics';
import SessionManagement from './pages/admin/SessionManagement';

function AppRouter() {
  // MapGamePageNew에 전달할 핸들러들
  const handleShowCreateRoom = () => {
    // App 컴포넌트의 상태를 업데이트하기 위해 커스텀 이벤트 발생
    window.dispatchEvent(new CustomEvent('showMinigameModal', {
      detail: { mode: 'create' }
    }));
  };

  const handleShowLobby = () => {
    // App 컴포넌트의 상태를 업데이트하기 위해 커스텀 이벤트 발생
    window.dispatchEvent(new CustomEvent('showMinigameModal', {
      detail: { mode: 'lobby' }
    }));
  };

  return (
    <BrowserRouter>
      <Routes>
        {/* 메인 게임 페이지 (로그인 화면 포함) */}
        <Route path="/" element={<App />} />
        <Route path="/game" element={<App />} />

        {/* 관리자 페이지 (ADMIN 또는 DEVELOPER 권한 필요) */}
        <Route
          path="/admin"
          element={
            <ProtectedRoute requiredRoles={["ROLE_ADMIN", "ROLE_DEVELOPER"]}>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Dashboard />} />
          <Route path="users" element={<UserManagement />} />
          <Route path="reports" element={<ReportManagement />} />
          <Route path="notices" element={<NoticeManagement />} />
          <Route path="chat-logs" element={<ChatLogManagement />} />
          <Route path="boards" element={<BoardManagement />} />
          <Route path="rooms" element={<div>게임 방 관리 (준비 중)</div>} />
          <Route path="shop" element={<ShopManagement />} />
          <Route path="payments" element={<PaymentManagement />} />
          <Route path="statistics" element={<Statistics />} />
          <Route path="audit-logs" element={<div>감사 로그 (준비 중)</div>} />
          <Route path="sessions" element={<SessionManagement />} />
          <Route path="profile-items" element={<ProfileItemManager />} />
          <Route path="system" element={<div>시스템 (준비 중)</div>} />
        </Route>

        <Route path="/minigame-select" element={<MinigameSelectPage />} />
        <Route 
          path="/map-game" 
          element={
            <MapGamePageNew 
              onShowCreateRoom={handleShowCreateRoom}
              onShowLobby={handleShowLobby}
            />
          } 
        />

        {/* 결제 위젯 팝업용 경로 */}
        <Route path="/payment/checkout" element={<PaymentCheckout />} />
        <Route path="/payment/callback/:status" element={<PaymentCallback />} />

        {/* 404 */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default AppRouter;
