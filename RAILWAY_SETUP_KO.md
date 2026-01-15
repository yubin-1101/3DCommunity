# Railway 배포 가이드 (한국어)

## 📋 요구사항
- Railway 계정 (railway.app)
- Railway CLI 설치
- Git 리포지토리

## 🚀 빠른 시작

### 1단계: Railway CLI 설치
```bash
npm install -g @railway/cli
```

### 2단계: Railway에 로그인
```bash
railway login
```

### 3단계: Frontend 배포

#### 방법 A: 프로젝트 루트에서 배포
```bash
cd 3DCommunity
railway init
railway up
```

#### 방법 B: GitHub 연결 (권장)
1. Railway Dashboard에서 "New Project" → "Deploy from GitHub"
2. GitHub 리포지토리 선택
3. `3DCommunity` 경로 설정

### 4단계: Backend 배포

```bash
cd backend
railway init
railway up
```

## 🔧 환경변수 설정 (Railway Dashboard)

### Frontend (.env)
```
NODE_ENV=production
REACT_APP_API_URL=https://backend-url.railway.app
REACT_APP_MAPBOX_TOKEN=pk_xxxxx
```

### Backend (application.properties/yml)
```properties
spring.datasource.url=jdbc:mysql://railway-mysql-db:3306/3dcommunity
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
server.port=8080
server.address=0.0.0.0
```

## 📊 Railway Dashboard에서 설정하기

### 1. Environment Variables 설정
1. Railway Dashboard → Project → Variables
2. 다음 변수 추가:
   - `NODE_ENV`: production
   - `PORT`: 3000 (Frontend) / 8080 (Backend)
   - `REACT_APP_API_URL`: Backend URL

### 2. Service 연결
1. Frontend와 Backend를 동일 프로젝트에서 실행
2. 또는 별도 프로젝트로 분리하고 API 엔드포인트 설정

### 3. Database 설정 (MySQL)
```bash
railway add  # MySQL 선택
```

## 🔗 API URL 설정

Frontend에서 Backend로의 요청:
```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

axios.defaults.baseURL = API_BASE_URL;
```

## 📝 Procfile 확인

### Frontend (Procfile)
```
web: npm run build && node server.js
```

### Backend (Procfile)
```
web: java -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -jar target/community-backend-0.0.1-SNAPSHOT.jar
```

## 🐛 배포 후 체크리스트

- [ ] Frontend 페이지 로드 확인
- [ ] Backend API 응답 확인
- [ ] Database 연결 확인
- [ ] 3D 모델 로딩 확인
- [ ] WebSocket (STOMP) 연결 확인
- [ ] 로그인/회원가입 동작 확인
- [ ] 실시간 채팅 기능 확인

## 🆘 문제해결

### Frontend 빌드 실패
```bash
# 로그 확인
railway logs

# npm 캐시 초기화
npm cache clean --force
rm -rf node_modules package-lock.json
npm install
```

### Backend JAR 파일을 찾을 수 없음
```bash
# 빌드 후 JAR 파일 확인
cd backend
./gradlew clean build -x test

# JAR 파일 확인
ls -la build/libs/
```

### API 요청 실패 (CORS)
Backend의 WebConfig에 CORS 설정 추가:
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("https://frontend-url.railway.app")
            .allowedMethods("*")
            .allowCredentials(true);
    }
}
```

## 💡 Tips

1. **Watch 로그**
   ```bash
   railway logs -f
   ```

2. **Shell 접근**
   ```bash
   railway shell
   ```

3. **프로젝트 삭제**
   ```bash
   railway project delete
   ```

4. **여러 서비스 관리**
   ```bash
   # 서비스 목록 확인
   railway services
   
   # 특정 서비스 로그
   railway logs -s backend
   ```

## 📞 Railway 공식 문서
- https://docs.railway.app
- https://railway.app/pricing

---

**마지막 수정**: 2026-01-15
