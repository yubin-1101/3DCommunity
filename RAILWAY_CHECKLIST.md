# Railway 빠른 시작 체크리스트

## 1️⃣ 사전 준비
- [ ] Railway 계정 생성 (https://railway.app)
- [ ] Railway CLI 설치: `npm install -g @railway/cli`
- [ ] GitHub 리포지토리 준비 또는 로컬 Git 설정
- [ ] 데이터베이스 준비 (Supabase, Railway MySQL, 또는 AWS RDS)

## 2️⃣ 프로젝트 구조 확인
```
3DCommunity/
├── Procfile              ✅ (생성됨)
├── railway.json          ✅ (생성됨)
├── server.js             ✅ (생성됨)
├── package.json          ✅ (업데이트됨)
├── src/                  (React 소스)
├── backend/
│   ├── Procfile          ✅ (생성됨)
│   ├── railway.json      ✅ (생성됨)
│   ├── railway.toml      ✅ (생성됨)
│   └── src/              (Spring Boot 소스)
```

## 3️⃣ Railway 배포 단계

### 방법 A: CLI 배포 (로컬)
```bash
# Frontend 배포
cd 3DCommunity
railway init
railway up
# URL 받기: railway service list

# Backend 배포 (다른 터미널)
cd 3DCommunity/backend
railway init
railway up
```

### 방법 B: GitHub 연결 (권장)
1. GitHub에 푸시
2. Railway Dashboard: "Create new project" → "Deploy from GitHub"
3. 리포지토리 선택
4. Configure 옵션에서 경로 설정

## 4️⃣ 환경변수 설정

### Frontend 환경변수 (Railway Dashboard)
```
Variables → Add
NODE_ENV = production
PORT = 3000
REACT_APP_API_URL = https://backend-url.railway.app
REACT_APP_MAPBOX_TOKEN = pk_xxxxx
```

### Backend 환경변수
```
SERVER_PORT = 8080
SPRING_DATASOURCE_URL = jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME = postgres
SPRING_DATASOURCE_PASSWORD = xxxxx
JWT_SECRET = your-secret-key-min-32-chars
CORS_ORIGINS = https://frontend-url.railway.app
```

## 5️⃣ Database 연결 (선택사항)

### Supabase 사용 (현재 설정)
- 기존 Supabase 자격증명 사용
- application.yml에 이미 설정됨

### Railway MySQL 사용
```bash
railway add mysql  # Database 추가
# 자동으로 DATABASE_URL 환경변수 생성
```

## 6️⃣ 배포 후 확인

### 로그 확인
```bash
railway logs          # Frontend 로그
railway logs -s backend  # Backend 로그
```

### 상태 확인
```bash
# 배포된 서비스 목록
railway service list

# 서비스 상세 정보
railway service info
```

### Health Check
```bash
curl https://your-backend-url.railway.app/actuator/health
```

## 7️⃣ 문제해결

### Frontend 빌드 실패
```bash
# 1. 로그 확인
railway logs

# 2. 로컬에서 빌드 테스트
npm install
npm run build

# 3. Node 버전 확인
node --version  # 16 이상 필요
```

### Backend 시작 안됨
```bash
# 1. 로그 확인
railway logs -s backend

# 2. 빌드 검증
cd backend
./gradlew clean build -x test

# 3. 포트 충돌 확인
lsof -i :8080
```

### API 요청 실패
1. REACT_APP_API_URL 확인
2. CORS_ORIGINS 설정 확인
3. Backend가 시작되었는지 확인
4. Database 연결 확인

### WebSocket 연결 실패
1. HTTPS/WSS 지원 확인
2. Backend의 STOMP 설정 확인
3. 브라우저 콘솔 에러 메시지 확인

## 8️⃣ 성능 최적화

### Frontend
```javascript
// .env에서 설정
REACT_APP_SKIP_PREFLIGHT_CHECK=true
GENERATE_SOURCEMAP=false
```

### Backend
```yaml
# application.yml
spring:
  jpa:
    show-sql: false  # 프로덕션에서는 false
  datasource:
    hikari:
      maximum-pool-size: 20  # 필요시 조정
```

## 9️⃣ 모니터링

### Railway Dashboard에서
- Deployments: 배포 이력 및 롤백
- Metrics: CPU, 메모리 사용량
- Logs: 실시간 로그
- Variables: 환경변수 관리

## 🔟 추가 리소스

- [Railway 공식 문서](https://docs.railway.app)
- [Railway Pricing](https://railway.app/pricing)
- [Node.js 배포 가이드](https://docs.railway.app/guides/nodejs)
- [Java 배포 가이드](https://docs.railway.app/guides/java)

---

**팁**: Railway는 주문형 요금 모델을 사용하므로 사용량에 따라 비용이 결정됩니다. $5 크레딧으로 시작할 수 있습니다.
