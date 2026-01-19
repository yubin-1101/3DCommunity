# 🚀 Railway 배포 완전 가이드

**작성일**: 2026-01-15  
**프로젝트**: 3D Community Platform

---

## 📌 목차
1. [사전 준비](#사전-준비)
2. [배포 방법](#배포-방법)
3. [환경변수 설정](#환경변수-설정)
4. [배포 후 확인](#배포-후-확인)
5. [문제해결](#문제해결)
6. [성능 최적화](#성능-최적화)

---

## 사전 준비

### 필수 설치 항목
```bash
# Railway CLI 설치
npm install -g @railway/cli

# 확인
railway --version
```

### 계정 생성
- https://railway.app에 가입
- Email 또는 GitHub 계정으로 로그인
- $5 초기 크레딧 받기

### Repository 준비
```bash
# 로컬 Git 저장소 초기화
cd 3DCommunity
git init
git add .
git commit -m "Initial commit for Railway deployment"

# GitHub로 푸시 (선택사항)
git remote add origin https://github.com/your-username/3DCommunity.git
git branch -M main
git push -u origin main
```

---

## 배포 방법

### 방법 1️⃣: CLI로 직접 배포 (가장 빠름)

#### Step 1: Railway 로그인
```bash
railway login
# 브라우저에서 인증 → Enter 키 누르기
```

#### Step 2: Frontend 배포
```bash
cd 3DCommunity
railway init

# 프로젝트 이름: 3d-community-frontend
# 환경: Production
```

배포 완료 후:
```bash
railway service list  # URL 확인
railway open         # 브라우저에서 열기
```

#### Step 3: Backend 배포
```bash
cd backend
railway init

# 프로젝트 이름: 3d-community-backend
# 환경: Production

railway up
```

---

### 방법 2️⃣: GitHub 자동 배포 (권장)

#### Step 1: GitHub에 푸시
```bash
git push origin main
```

#### Step 2: Railway Dashboard에서 설정
1. https://dashboard.railway.app 접속
2. "Create new project"
3. "Deploy from GitHub"
4. GitHub 인증 및 리포지토리 선택

#### Step 3: Configure
- **Root Directory (Frontend)**: `.` (루트)
- **Server Entry Point**: `server.js`

#### Step 4: Backend 추가
1. "Add Service" → "From Repository"
2. 동일 리포지토리 선택
3. **Root Directory**: `backend`

---

## 환경변수 설정

### Dashboard에서 설정

#### Frontend (3d-community-frontend)
```
Variables → Add Variable
```

| 키 | 값 |
|---|---|
| `NODE_ENV` | `production` |
| `PORT` | `3000` |
| `REACT_APP_API_URL` | `https://3d-community-backend.railway.app` |
| `REACT_APP_MAPBOX_TOKEN` | `pk_xxxxx` (Mapbox 토큰) |

#### Backend (3d-community-backend)
```
Variables → Add Variable
```

| 키 | 값 |
|---|---|
| `SERVER_PORT` | `8080` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-region.pooler.supabase.com:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `postgres.xxxxx` |
| `SPRING_DATASOURCE_PASSWORD` | `xxxxx` |
| `JWT_SECRET` | `your-32-character-secret-key-here` |
| `CORS_ORIGINS` | `https://3d-community-frontend.railway.app` |
| `TOSS_SECRET_KEY` | `test_sk_xxxxx` |
| `TOSS_CLIENT_KEY` | `test_ck_xxxxx` |
| `DB_DDL_AUTO` | `update` |
| `DB_SHOW_SQL` | `false` |

### 또는 CLI로 설정
```bash
railway variables add NODE_ENV=production
railway variables add REACT_APP_API_URL=https://backend-url.railway.app
```

---

## 배포 후 확인

### 1. 배포 상태 확인
```bash
# 서비스 목록 및 상태
railway service list

# 자세한 정보
railway service info
```

### 2. 로그 확인
```bash
# Frontend 로그 (실시간)
railway logs -f

# Backend 로그
railway logs -f -s backend

# 마지막 100줄
railway logs -n 100
```

### 3. Health Check

#### Frontend
```bash
# 페이지 로드 확인
curl https://3d-community-frontend.railway.app
```

#### Backend
```bash
# Health Check 엔드포인트
curl https://3d-community-backend.railway.app/actuator/health
```

응답 예시:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL"
      }
    }
  }
}
```

### 4. 기능 테스트
- [ ] 메인 페이지 로드
- [ ] 사용자 회원가입/로그인
- [ ] 3D 모델 로딩
- [ ] 실시간 채팅
- [ ] 맵 네비게이션
- [ ] 아이템 구매 (Toss Payment)
- [ ] WebSocket 연결

---

## 문제해결

### ❌ Frontend가 로드되지 않음

#### 로그 확인
```bash
railway logs -f
```

#### 해결 방법
1. **npm 캐시 초기화**
   ```bash
   npm cache clean --force
   rm -rf node_modules package-lock.json
   npm install
   git add . && git commit -m "Clean npm"
   git push
   ```

2. **Node 버전 확인**
   ```bash
   railway variables add NODE_VERSION=18
   ```

3. **빌드 환경 변수**
   ```bash
   railway variables add \
     NPM_FLAGS="--legacy-peer-deps" \
     GENERATE_SOURCEMAP=false
   ```

---

### ❌ Backend 시작되지 않음

#### 로그 확인
```bash
railway logs -f -s backend
```

#### 일반적인 원인
1. **JAR 파일 빌드 실패**
   ```bash
   cd backend
   ./gradlew clean build -x test
   ```

2. **Database 연결 실패**
   - `SPRING_DATASOURCE_URL` 확인
   - `SPRING_DATASOURCE_USERNAME` 확인
   - `SPRING_DATASOURCE_PASSWORD` 확인

3. **포트 설정**
   ```bash
   railway variables add SERVER_PORT=8080
   ```

4. **메모리 부족**
   ```bash
   railway variables add JAVA_OPTS="-Xmx512m -XX:MaxMetaspaceSize=256m"
   ```

---

### ❌ API 요청 실패 (CORS 에러)

```
Access to XMLHttpRequest has been blocked by CORS policy
```

#### 해결 방법
1. Backend의 `CORS_ORIGINS` 확인
   ```bash
   railway variables -s backend | grep CORS_ORIGINS
   ```

2. 값 업데이트
   ```bash
   railway variables add CORS_ORIGINS=https://frontend-url.railway.app -s backend
   ```

3. Backend 재시작
   ```bash
   railway redeploy -s backend
   ```

---

### ❌ WebSocket 연결 실패

```
WebSocket connection failed
```

#### 원인
- HTTPS/WSS 미지원
- STOMP 엔드포인트 설정 오류

#### 확인 방법
```javascript
// 브라우저 콘솔에서
console.log(window.location.protocol);  // https: 확인
```

---

### ❌ 3D 모델 로딩 실패

#### 원인
- 리소스 경로 오류
- CORS 문제

#### 해결
1. **경로 확인**
   ```bash
   # public/resources/ 파일 확인
   ls -la public/resources/
   ```

2. **CORS 헤더 확인**
   ```bash
   curl -i https://your-url.railway.app/resources/model.gltf
   ```

---

## 성능 최적화

### Frontend 최적화

#### 1. 빌드 최적화
```bash
# .env 파일
GENERATE_SOURCEMAP=false
REACT_APP_SKIP_PREFLIGHT_CHECK=true
```

#### 2. 캐싱 설정
`server.js`에 캐시 헤더 추가:
```javascript
app.use((req, res, next) => {
  if (req.url.match(/\.(js|css|png|jpg|gif|woff)$/)) {
    res.set('Cache-Control', 'public, max-age=31536000');
  }
  next();
});
```

#### 3. 번들 크기 분석
```bash
npm install -g webpack-bundle-analyzer
npm run build
# build/index.html 분석
```

### Backend 최적화

#### 1. 데이터베이스 커넥션 풀
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

#### 2. 쿼리 성능
```java
// N+1 쿼리 방지
@EntityGraph(attributePaths = {"posts", "comments"})
List<User> findAll();
```

#### 3. 캐싱
```java
@Cacheable("users")
public User findById(Long id) {
  return userRepository.findById(id);
}
```

---

## 모니터링 및 유지보수

### Railway Dashboard
- **Deployments**: 배포 이력, 빠른 롤백
- **Metrics**: CPU, 메모리 사용량 모니터링
- **Logs**: 실시간 로그 스트리밍
- **Variables**: 환경변수 동적 변경

### 로그 모니터링
```bash
# 실시간 로그 보기
railway logs -f

# 오류만 필터링
railway logs | grep ERROR

# 특정 서비스 로그
railway logs -s backend -n 50
```

### 리소스 모니터링
```bash
# CPU 및 메모리 사용량
railway service info

# 더 자세한 메트릭
railway metrics
```

---

## 배포 자동화

### GitHub Actions로 자동 배포

`.github/workflows/railway-deploy.yml`:
```yaml
name: Deploy to Railway

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Deploy Frontend
        run: railway up
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
      - name: Deploy Backend
        run: cd backend && railway up
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
```

Railway Token 생성:
1. Railway Dashboard → Account Settings
2. API Token 생성
3. GitHub Secrets → `RAILWAY_TOKEN` 추가

---

## 비용 관리

### Railway 가격 정책
- **컴퓨팅**: 시간당 사용량 기반
- **스토리지**: GB당 월 요금
- **대역폭**: 아웃바운드 요금

### 비용 절감
1. 불필요한 서비스 제거
2. 리소스 사용량 모니터링
3. 개발 환경 별도 프로젝트 사용
4. 자동 스케일링 설정

### 결제 확인
```bash
railway billing
```

---

## 추가 리소스

- 📖 [Railway 공식 문서](https://docs.railway.app)
- 🚀 [Node.js 배포 가이드](https://docs.railway.app/guides/nodejs)
- ☕ [Java 배포 가이드](https://docs.railway.app/guides/java)
- 💬 [Railway 커뮤니티](https://railway.app/community)

---

## 긴급 연락처

문제 발생 시:
```bash
# 디버그 모드 로그
railway logs -d -f

# 서비스 상태 재설정
railway service restart

# 강제 재배포
railway redeploy
```

---

**마지막 수정**: 2026-01-15  
**버전**: 1.0
