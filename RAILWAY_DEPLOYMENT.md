# Railway 배포 설정 가이드

## 프로젝트 구조
- **Frontend**: React (포트 3000)
- **Backend**: Spring Boot (포트 8080)

## Railway 배포 방법

### 1. Frontend 배포
```bash
# Railway CLI로 프로젝트 로그인
railway login

# Frontend 디렉토리에서 배포
cd 3DCommunity
railway up
```

### 2. Backend 배포 (선택사항)
```bash
# Backend 디렉토리에서 배포
cd 3DCommunity/backend
railway up
```

## 환경변수 설정 (Railway Dashboard)

### Frontend 환경변수
```
NODE_ENV=production
REACT_APP_API_URL=https://your-backend-url.railway.app
REACT_APP_MAPBOX_TOKEN=your_mapbox_token
```

### Backend 환경변수
```
SPRING_DATASOURCE_URL=jdbc:mysql://your-db-host:3306/your_db
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_password
SPRING_JPA_HIBERNATE_DDL_AUTO=update
JWT_SECRET=your_jwt_secret_key
```

## 배포 후 확인사항

1. ✅ Frontend이 정상 로드되는지 확인
2. ✅ Backend API 통신이 정상인지 확인
3. ✅ WebSocket 연결 (STOMP) 확인
4. ✅ 3D 모델 및 리소스 로딩 확인
5. ✅ Database 연결 확인

## 문제해결

### Frontend가 로드되지 않음
- `npm run build` 성공 여부 확인
- Railway 로그에서 빌드 오류 확인

### API 요청 실패
- BACKEND_URL 환경변수 설정 확인
- CORS 설정 확인
- Backend 로그에서 에러 확인

### WebSocket 연결 실패
- Backend WebSocket 설정 확인
- 방화벽 설정 확인
