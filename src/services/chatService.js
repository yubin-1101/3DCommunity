import axios from 'axios';
import authService from './authService';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Axios 인스턴스 생성
const apiClient = axios.create({
    baseURL: `${API_BASE_URL}/api`,
    headers: {
        'Content-Type': 'application/json'
    }
});

// 요청 인터셉터: 토큰 자동 추가
apiClient.interceptors.request.use(
    (config) => {
        const token = authService.getToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// 응답 인터셉터: 에러 처리
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            // 인증 실패 시 로그아웃 처리 (필요에 따라 주석 해제)
            // authService.logout();
            // window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

// 채팅 API
export const chatAPI = {
    // 대화방 목록 조회
    getRooms: () =>
        apiClient.get('/chat/rooms'),

    // 대화방 생성
    createRoom: (type, inviteUserIds, title = null) =>
        apiClient.post('/chat/rooms', { type, inviteUserIds, title }),

    // 메시지 내역 조회 (페이징)
    getMessages: (roomId, lastId = null, size = 50) =>
        apiClient.get(`/chat/rooms/${roomId}/messages`, {
            params: { lastId, size }
        }),

    // 메시지 전송 (REST API 기반 - 소켓으로 보낼 수도 있음)
    sendMessage: (roomId, content) =>
        apiClient.post(`/chat/rooms/${roomId}/messages`, { content }),

    // 읽음 처리
    markAsRead: (roomId, lastMessageId) =>
        apiClient.patch(`/chat/rooms/${roomId}/read`, { lastMessageId }),

    // 사용자 초대
    inviteUsers: (roomId, userIds) =>
        apiClient.post(`/chat/rooms/${roomId}/invite`, { userIds }),

    // 방 나가기
    leaveRoom: (roomId) =>
        apiClient.delete(`/chat/rooms/${roomId}/leave`),

    // 방 제목 수정
    updateRoomTitle: (roomId, title) =>
        apiClient.patch(`/chat/rooms/${roomId}`, { title })
};

export default chatAPI;
