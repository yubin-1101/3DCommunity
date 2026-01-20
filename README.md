# 🌐 MetaPlaza - 3D 소셜 메타버스

> **현재 브랜치**: `kim` (활성 개발) | **마지막 업데이트**: 2026-01-09

React, Three.js, Spring Boot를 활용한 3D 소셜 커뮤니티 플랫폼입니다. 가상 공간에서 다른 사용자들과 실시간으로 만나고 소통하며 다양한 활동을 즐길 수 있습니다.

## ✨ 주요 기능

### 🎮 3D 가상 광장
- **실시간 3D 렌더링**: Three.js와 React Three Fiber를 활용한 3D 그래픽
- **물리 엔진**: React Three Rapier로 현실감 있는 움직임 구현
- **동적 조명**: 그림자와 조명 효과가 적용된 몰입감 있는 환경
- **실시간 멀티플레이어**: WebSocket(STOMP)을 통한 실시간 사용자 동기화

### 👤 회원 시스템
- **회원가입/로그인**: JWT 기반 인증 시스템
- **프로필 관리**: 닉네임, 레벨, 프로필 이미지 커스터마이징
- **관리자 권한**: ROLE_USER, ROLE_DEVELOPER 역할 기반 접근 제어

### 🚶‍♂️ 캐릭터 시스템
- **3D 캐릭터**: BaseCharacter.gltf 기반 캐릭터
- **애니메이션**: Idle, Walk, Run 상태 자동 전환
- **키보드 조작**: WASD/방향키로 이동, Shift로 달리기
- **상점 아바타**: 상점에서 구매한 아바타로 변경 가능
- **실시간 동기화**: 다른 플레이어들에게 내 위치/애니메이션 실시간 전송

### 💬 실시간 소셜 기능
- **전역 채팅**: 광장 내 모든 사용자와 실시간 채팅
- **채팅 말풍선**: 캐릭터 위에 말풍선으로 메시지 표시 (5초간)
- **친구 시스템**: 친구 추가, 친구 목록 관리
- **DM(Direct Message)**: 친구와 1:1 채팅
- **온라인 상태**: 실시간 접속자 수 표시
- **사용자 프로필**: 다른 플레이어 우클릭으로 프로필 보기

### 📋 게시판 시스템
- **게시판**: 자유게시판, 공지사항 등 다양한 게시판
- **게시글 작성/수정/삭제**: 로그인한 사용자만 작성 가능
- **댓글**: 게시글에 댓글 작성/삭제
- **좋아요**: 게시글/댓글 좋아요 기능
- **신고**: 부적절한 게시글/댓글 신고

### 🎮 미니게임 시스템
- **게임 로비**: 미니게임 방 목록 조회, 관전자 수 실시간 표시
- **방 생성**: 사용자가 게임 방 생성 (게임 선택, 인원 설정)
- **게임 참가**: 다른 사용자가 만든 방에 입장
- **대기방**: 중복 입장 방지, 참가자 목록 표시
- **게임 관전**: 진행 중인 게임 관전 기능
- **게임 초대**: 친구를 게임에 초대
- **구현된 게임**:
  - 오목 (Omok): 2인 대전
  - 반응속도 게임 (Reaction Race): 여러 명 참여 가능

### 💰 경제 시스템
- **재화**: Silver Coin(일반 재화), Gold Coin(유료 재화)
- **상점**: 아바타, 프로필 이미지, 아웃라인 이미지 구매
- **인벤토리**: 구매한 아이템 관리 및 착용
- **결제**: TossPayments 연동 (Gold Coin 충전)
- **재화 교환**: Gold Coin을 Silver Coin으로 교환
- **출석 체크**: 매일 출석하여 Silver/Gold Coin 획득

### 🗺️ 지도 연동
- **Mapbox 지도**: 실제 지리 정보 연동
- **GPS 위치**: 사용자의 현재 위치 기반 캐릭터 스폰
- **지도 모드**: 3D 광장과 지도 모드 전환 가능

### 🔔 알림 시스템
- **친구 요청 알림**: 친구 요청 수신 시 실시간 알림
- **게임 초대 알림**: 게임 초대 수신 시 알림
- **DM 알림**: 메시지 수신 시 알림
- **토스트 알림**: 화면 우측 상단에 팝업 알림
- **알림 센터**: 과거 알림 이력 조회

### 👑 관리자 기능
- **유저 관리**: 사용자 목록, 역할 변경
- **게시판 관리**: 게시글/댓글 삭제, 공지사항 작성
- **신고 관리**: 신고된 게시글/댓글 검토 및 처리
- **제재**: 사용자 정지 (기간 설정 가능)
- **통계**: 회원 수, 게시글 수 등 대시보드

## 🎮 조작법

| 키 | 기능 |
|---|---|
| `W` / `↑` | 앞으로 이동 |
| `S` / `↓` | 뒤로 이동 |
| `A` / `←` | 왼쪽으로 이동 |
| `D` / `→` | 오른쪽으로 이동 |
| `Shift` | 달리기 (이동 키와 함께) |
| `Enter` | 채팅 입력 |
| `ESC` | 메뉴 닫기 |
| `C` | 카메라 위치 로그 출력 (개발용) |
| **우클릭** | 다른 플레이어 우클릭 시 메뉴 (프로필 보기, 친구 추가) |

## 🛠️ 기술 스택

### Frontend
- **React 19.1.1**: 최신 React
- **Three.js 0.179.1**: 3D 그래픽 렌더링
- **React Three Fiber 9.3.0**: React와 Three.js 통합
- **React Three Drei 10.7.4**: Three.js 헬퍼 라이브러리
- **React Three Rapier 2.2.0**: 물리 엔진
- **React Router 7.9.6**: SPA 라우팅
- **Axios 1.13.2**: HTTP 통신
- **STOMP/SockJS**: 실시간 WebSocket 통신
- **Mapbox GL 2.15.0**: 지도 서비스
- **Supabase JS 2.86.0**: 데이터베이스 클라이언트
- **jwt-decode 4.0.0**: JWT 디코딩
- **TossPayments SDK 1.9.2**: 결제 시스템

### Backend
- **Spring Boot 3.2.0**: 메인 백엔드 프레임워크
- **Java 17**: JDK 버전
- **Spring Security**: 인증 및 권한 관리
- **JWT (JJWT 0.12.3)**: 토큰 기반 인증
- **Spring Data JPA**: 데이터베이스 ORM
- **Spring WebSocket**: 실시간 통신
- **PostgreSQL**: 관계형 데이터베이스 (Supabase)
- **Gradle**: 빌드 툴

### 배포
- **Frontend**: Netlify (자동 배포)
- **Backend**: Render (Docker 컨테이너)
- **Database**: Supabase PostgreSQL

## 🚀 설치 및 실행

### 필요 조건
- **Frontend**: Node.js 18.0 이상, npm
- **Backend**: JDK 17 이상, Gradle
- **Database**: PostgreSQL (Supabase 사용)

### Frontend 설치 및 실행
```bash
# 저장소 클론
git clone https://github.com/kimkichan1225/3DCommunity
cd MetaPlaza

# 의존성 설치 (peer dependency 경고로 인해 --legacy-peer-deps 필요)
npm install --legacy-peer-deps

# 개발 서버 실행
npm start
```
브라우저에서 `http://localhost:3000`으로 접속

### Backend 설정 및 실행
```bash
# Backend 디렉토리로 이동
cd backend

# Gradle 빌드 및 실행 (Windows)
gradlew bootRun

# Gradle 빌드 및 실행 (Mac/Linux)
./gradlew bootRun

# 또는 IntelliJ IDEA에서 CommunityApplication.java 실행 (권장)
```
백엔드 서버는 `http://localhost:8080`에서 실행

### 환경 변수 설정

Frontend `.env` 파일:
```env
REACT_APP_API_URL=http://localhost:8080
REACT_APP_MAPBOX_TOKEN=your_mapbox_token_here
```

Backend `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://your-supabase-url:5432/postgres
    username: your_username
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update

jwt:
  secret: your_jwt_secret_key
  expiration: 86400000  # 24시간
```

## 🌐 배포

### Frontend 배포 (Netlify)
```bash
# 프로덕션 빌드
npm run build

# Netlify CLI로 배포
netlify deploy --prod
```

**배포 설정**:
- **빌드 명령어**: `npm run build`
- **배포 디렉토리**: `build`
- **Node 버전**: 18
- **환경 변수**: `.env` 파일의 환경 변수를 Netlify 설정에 추가

### Backend 배포 (Render)
**Dockerfile 기반 배포**:
- Docker 이미지를 사용하여 Render에 자동 배포
- `backend/Dockerfile`에서 빌드 및 실행 설정
- 환경 변수는 Render 대시보드에서 설정
- PostgreSQL 데이터베이스는 Supabase 연결

## 📁 프로젝트 구조

```
MetaPlaza/
├── frontend/
│   ├── public/
│   │   └── resources/
│   │       ├── Ultimate Animated Character Pack/  # 캐릭터 모델
│   │       ├── GameView/                         # 맵 모델
│   │       ├── Icon/                             # UI 아이콘
│   │       └── Sounds/                           # 오디오 파일
│   ├── src/
│   │   ├── features/                   # 기능별 모듈 (feature-based)
│   │   │   ├── auth/                  # 로그인/회원가입
│   │   │   ├── board/                 # 게시판
│   │   │   ├── profile/               # 프로필
│   │   │   ├── shop/                  # 상점
│   │   │   ├── inventory/             # 인벤토리
│   │   │   ├── payment/               # 결제
│   │   │   ├── event/                 # 이벤트/출석체크
│   │   │   ├── minigame/              # 미니게임
│   │   │   ├── map/                   # 지도
│   │   │   └── system/settings/       # 설정
│   │   ├── components/                 # 공용 컴포넌트
│   │   │   ├── character/             # 캐릭터 관련
│   │   │   ├── camera/                # 카메라 관련
│   │   │   ├── map/                   # 맵 관련
│   │   │   ├── GlobalChat.jsx         # 전역 채팅
│   │   │   ├── PhoneUI.jsx            # 친구/DM UI
│   │   │   ├── Notification.jsx       # 알림
│   │   │   └── ...
│   │   ├── services/                   # API 서비스
│   │   │   ├── multiplayerService.js  # 멀티플레이어 WebSocket
│   │   │   ├── minigameService.js     # 미니게임 WebSocket
│   │   │   ├── friendService.js       # 친구 API
│   │   │   ├── currencyService.js     # 재화 API
│   │   │   ├── attendanceService.js   # 출석 API
│   │   │   └── notificationService.js # 알림 서비스
│   │   ├── pages/admin/                # 관리자 페이지
│   │   ├── App.js                      # 메인 앱
│   │   ├── AppRouter.jsx               # 라우터
│   │   └── index.js                    # 엔트리 포인트
│   └── package.json
│
├── backend/
│   ├── src/main/java/com/community/
│   │   ├── controller/         # REST API 컨트롤러
│   │   │   ├── AuthController.java
│   │   │   ├── BoardController.java
│   │   │   ├── PostController.java
│   │   │   ├── CommentController.java
│   │   │   ├── FriendController.java
│   │   │   ├── MessageController.java
│   │   │   ├── ProfileController.java
│   │   │   ├── ShopController.java
│   │   │   ├── PaymentController.java
│   │   │   ├── CurrencyController.java
│   │   │   ├── AttendanceController.java
│   │   │   ├── MinigameController.java
│   │   │   ├── MultiplayerController.java
│   │   │   ├── ReportController.java
│   │   │   ├── LikeController.java
│   │   │   ├── NoticeController.java
│   │   │   ├── ProfileItemController.java
│   │   │   ├── UserShopController.java
│   │   │   ├── AdminController.java
│   │   │   ├── AdminBoardController.java
│   │   │   ├── AdminProfileController.java
│   │   │   └── ...
│   │   ├── service/            # 비즈니스 로직
│   │   ├── repository/         # JPA Repository
│   │   ├── model/              # JPA Entity
│   │   │   ├── User.java
│   │   │   ├── Profile.java
│   │   │   ├── Board.java
│   │   │   ├── Post.java
│   │   │   ├── Comment.java
│   │   │   ├── Friendship.java
│   │   │   ├── Message.java
│   │   │   ├── ShopItem.java
│   │   │   ├── UserShop.java
│   │   │   ├── Currency.java
│   │   │   ├── Attendance.java
│   │   │   └── ...
│   │   ├── dto/                # DTO
│   │   ├── security/           # JWT 인증
│   │   │   ├── JwtTokenProvider.java
│   │   │   └── JwtAuthenticationFilter.java
│   │   └── config/
│   │       ├── SecurityConfig.java
│   │       ├── WebSocketConfig.java
│   │       └── DataInitializer.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── build.gradle
│
└── README.md
```

## 📊 주요 API 엔드포인트

### 인증
- `POST /api/auth/register` - 회원가입
- `POST /api/auth/login` - 로그인
- `POST /api/auth/logout` - 로그아웃

### 프로필
- `GET /api/profile` - 현재 사용자 프로필 조회
- `PUT /api/profile` - 프로필 수정

### 게시판
- `GET /api/boards` - 게시판 목록
- `GET /api/boards/{id}/posts` - 게시글 목록
- `POST /api/posts` - 게시글 작성
- `GET /api/posts/{id}` - 게시글 상세
- `PUT /api/posts/{id}` - 게시글 수정
- `DELETE /api/posts/{id}` - 게시글 삭제
- `POST /api/posts/{id}/comments` - 댓글 작성
- `POST /api/posts/{id}/like` - 게시글 좋아요

### 친구
- `GET /api/friends` - 친구 목록
- `POST /api/friends/request` - 친구 요청
- `POST /api/friends/accept/{id}` - 친구 수락
- `POST /api/friends/reject/{id}` - 친구 거절
- `DELETE /api/friends/{id}` - 친구 삭제

### 메시지 (DM)
- `GET /api/messages/{friendId}` - DM 내역 조회
- WebSocket `/app/dm.send` - DM 전송

### 상점
- `GET /api/shop/items` - 상점 아이템 목록
- `POST /api/shop/purchase` - 아이템 구매
- `GET /api/usershop/inventory` - 인벤토리 조회
- `POST /api/usershop/equip/{id}` - 아이템 착용

### 재화
- `GET /api/currency` - 재화 조회
- `POST /api/currency/exchange` - Gold Coin → Silver Coin 교환

### 결제
- `POST /api/payment/charge` - Gold Coin 충전 (TossPayments)
- `POST /api/payment/verify` - 결제 검증

### 출석 체크
- `POST /api/attendance/check` - 출석 체크
- `GET /api/attendance/today` - 오늘 출석 여부

### 좋아요
- `POST /api/likes/post/{postId}` - 게시글 좋아요
- `POST /api/likes/comment/{commentId}` - 댓글 좋아요
- `DELETE /api/likes/post/{postId}` - 게시글 좋아요 취소

### 공지사항
- `GET /api/notices` - 공지사항 목록
- `POST /api/notices` - 공지사항 작성 (관리자)
- `PUT /api/notices/{id}` - 공지사항 수정 (관리자)
- `DELETE /api/notices/{id}` - 공지사항 삭제 (관리자)

### 미니게임
- `POST /api/minigame/room/create` - 게임 방 생성
- `POST /api/minigame/room/join/{roomId}` - 게임 방 입장
- `POST /api/minigame/room/leave/{roomId}` - 게임 방 나가기
- `GET /api/minigame/rooms` - 게임 방 목록

### WebSocket
- `/app/multiplayer.connect` - 멀티플레이어 접속
- `/app/multiplayer.move` - 위치 업데이트
- `/app/chat.send` - 전역 채팅 전송
- `/app/dm.send` - DM 전송
- `/app/minigame.*` - 미니게임 이벤트
- `/app/friend.*` - 친구 관련 이벤트

## 🎨 주요 기능 상세

### 실시간 멀티플레이어
- WebSocket(STOMP) 기반 실시간 통신
- 사용자 위치/회전/애니메이션 동기화
- 접속/퇴장 알림
- 온라인 인원 수 실시간 표시
- 중복 로그인 방지

### 채팅 시스템
- **전역 채팅**: 광장 내 모든 사용자와 채팅
- **채팅 말풍선**: 캐릭터 위 5초간 말풍선 표시
- **DM**: 친구와 1:1 대화
- **입력 중 이동 차단**: 채팅 입력 시 캐릭터 이동 자동 차단

### 미니게임
- **오목**: 15x15 보드에서 2인 대전, 5개 연속 배치 시 승리
- **반응속도 게임**: 신호에 빠르게 반응하여 순위 경쟁
- **로비 시스템**:
  - 실시간 방 목록 조회
  - 관전자 수 표시 (게임 중인 방)
  - 중복 입장 방지
  - 대기방에서 참가자 목록 확인

### 경제 시스템
- **Silver Coin**: 출석 체크, 게임 보상으로 획득
- **Gold Coin**: 실제 결제로 충전, Silver Coin으로 교환 가능
- **상점**: 아바타, 프로필, 아웃라인 구매
- **인벤토리**: 구매 아이템 관리 및 착용

## 🔧 개발 정보

### 브라우저 지원
- Chrome, Firefox, Safari, Edge (WebGL 2.0 지원 필수)
- 모바일 브라우저 (터치 조작 미지원)

### 성능 최적화
- 3D 모델 프리로드로 로딩 시간 단축
- 모델 클론으로 메모리 효율화
- WebSocket 재연결 로직
- 그림자 맵 품질 조절 (설정에서 변경 가능)

## 🛠️ 문제해결 사례

### 백엔드 시작 시간 최적화 (2026-01-20)

#### 문제
- Spring Boot 백엔드 시작 시간이 과도하게 오래 걸림
- DataInitializer에서 매번 모든 사용자를 조회하여 기본 아이템 지급
- SQL 쿼리 로깅 및 DEBUG 레벨 로깅으로 콘솔 출력 오버헤드 발생
- WebSocket 연결 시 `NullPointerException` 발생

#### 해결책

**1. application.yml 최적화**
```yaml
spring:
  main:
    lazy-initialization: true  # 빈 지연 로딩 활성화
  jpa:
    show-sql: false            # SQL 로깅 비활성화
    hibernate:
      ddl-auto: none           # 스키마 검증 생략 (이미 존재하는 경우)
    properties:
      hibernate:
        jdbc:
          batch_size: 20       # 배치 처리 설정
        order_inserts: true
        order_updates: true

logging:
  level:
    root: WARN                 # 로깅 레벨 최적화
    com.community: INFO
    org.hibernate: WARN
```

**2. DataInitializer 최적화**
- `@Transactional` 추가로 단일 트랜잭션 처리
- `saveAll()` 배치 처리로 개별 쿼리 감소
- 이미 초기화된 경우 빠르게 스킵하는 로직 추가
- `grantDefaultItemsToAllUsers()` 제거 (매번 전체 사용자 조회하던 병목)

**3. WebSocketEventListener 수정**
```java
// 세션 속성 null 체크 추가
var sessionAttributes = headerAccessor.getSessionAttributes();
if (sessionAttributes == null) {
    log.debug("Session attributes is null, skipping event");
    return;
}
```

**4. FriendService 트랜잭션 추가**
```java
@Service
@Transactional(readOnly = true)  // 클래스 레벨 트랜잭션 추가
public class FriendService { ... }
```

#### 결과
| 항목 | 변경 전 | 변경 후 |
|------|---------|---------|
| DataInitializer | 수 초 | ~1초 |
| SQL 로깅 | 활성화 (콘솔 스팸) | 비활성화 |
| 로깅 레벨 | DEBUG | WARN/INFO |
| WebSocket 오류 | NullPointerException | 정상 작동 |
| 친구 목록 API | 500 오류 | 정상 작동 |

#### 주의사항
- `ddl-auto: none`으로 변경 시, 새 엔티티 추가하면 DB 스키마 수동 업데이트 필요
- 스키마 변경이 필요한 경우: `DB_DDL_AUTO=update` 환경변수 사용 또는 yml 임시 변경
- `open-in-view: true` 유지 (Lazy Loading 문제 방지)

---

### Git 브랜치 전략
- `main`: 프로덕션 코드 (배포용)
- `kim`: 현재 활성 개발 브랜치 (2025-12-04 기준)
- `kichan`: 기능 브랜치

**작업 흐름**:
1. `kim` 브랜치에서 최신 코드 pull
2. 기능별 `features/` 디렉토리에서 작업
3. 커밋 및 `kim` 브랜치에 push
4. 충분한 테스트 후 `main` 브랜치로 병합

**최근 업데이트** (2026-01-09):
- ✨ feat: 로비에 관전자 수 항상 표시
- 🐛 fix: 로비 중복 방 표시 문제 해결 및 레이아웃 가로 배치
- 🐛 fix: 로비 방 목록 출력 및 게임 중 관전자 입장 기능 개선
- 🐛 fix: 대기방 중복 입장 방지 기능 추가
- 🚀 fix: Render 배포를 위한 Dockerfile 최적화

## 📝 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📧 연락처

- GitHub: [@kimkichan1225](https://github.com/kimkichan1225)
- Repository: [3DCommunity](https://github.com/kimkichan1225/3DCommunity)

---

**MetaPlaza에서 새로운 소셜 경험을 시작하세요! 🌐✨**
