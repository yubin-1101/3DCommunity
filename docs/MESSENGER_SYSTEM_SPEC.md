# Messenger System Specification

## 1. 개요 (Overview)
`3Dcommu` 플랫폼 내 사용자 간의 소통을 강화하기 위한 **실시간 메신저 시스템**입니다. 기존의 광장(Plaza) 전체 채팅을 넘어, 특정 사용자와의 **1:1 비공개 대화(DM)** 및 다수 사용자가 참여하는 **그룹 대화(Group Chat)**를 지원합니다.

본 문서는 현재 구현된 단순 DM 기능을 확장하여, **Room 기반의 확장 가능한 채팅 아키텍처**로 전환하기 위한 요구사항 및 설계를 정의합니다.

---

## 2. 요구사항 명세서 (SRS)

### 2.1 기능적 요구사항 (Functional Requirements)

#### A. 대화방 관리 (Chat Room Management)
1.  **방 생성 (Create Room)**
    *   **1:1 채팅:** 특정 친구를 선택하여 대화를 시작합니다. (기존 대화방이 있으면 해당 방을 엽니다.)
    *   **그룹 채팅:** 2명 이상의 사용자를 선택하여 새로운 그룹 채팅방을 생성합니다.
2.  **방 목록 조회 (List Rooms)**
    *   내가 참여 중인 모든 대화방(DM, Group)의 목록을 최신순으로 조회합니다.
    *   각 항목에는 `방 제목`, `마지막 메시지`, `마지막 시간`, `읽지 않은 메시지 수(Badge)`가 표시되어야 합니다.
3.  **방 정보 수정 (Update Room)**
    *   그룹 채팅방의 경우, 방 제목(Title)을 수정할 수 있어야 합니다.
4.  **방 나가기/초대 (Leave/Invite)**
    *   사용자는 언제든지 방에서 나갈 수 있습니다.
    *   그룹 채팅방에는 새로운 사용자를 초대할 수 있습니다.

#### B. 메시지 처리 (Messaging)
1.  **메시지 전송 (Send)**
    *   텍스트 메시지를 전송할 수 있습니다.
    *   (추후 확장) 이미지, 이모티콘 등 멀티미디어 전송을 지원해야 합니다.
2.  **실시간 수신 (Real-time Receive)**
    *   접속 중인 경우, 새로운 메시지를 실시간으로 수신하여 화면에 표시합니다.
    *   다른 화면에 있는 경우, Toast 알림 또는 상단 배지(Badge)로 알립니다.
3.  **대화 기록 조회 (History)**
    *   방에 입장 시, 최근 대화 내역을 불러옵니다.
    *   스크롤을 올릴 때 과거 내역을 페이징(Infinite Scroll)하여 가져옵니다.
4.  **읽음 처리 (Read Status)**
    *   상대방이 메시지를 읽었는지 알 수 있어야 합니다. (1:1: 읽음 표시, 그룹: 안 읽은 사람 수 표시)

#### C. 시스템 연동 (Integration)
1.  **기존 광장 채팅 연동:** `MultiplayerController`의 로직과 충돌하지 않도록 메신저 로직을 분리 또는 통합 관리합니다.
2.  **사용자 프로필:** 메시지 표시 시 `ProfileAvatar` 시스템을 활용하여 유저의 커스터마이징된 아바타를 보여줍니다.

### 2.2 비기능적 요구사항 (Non-Functional Requirements)
1.  **확장성:** 1:1 구조에서 N:M 구조로 DB 스키마를 변경하여 그룹 채팅을 지원해야 합니다.
2.  **성능:** 메시지 전송 후 수신까지 500ms 이내의 지연 시간을 목표로 합니다.
3.  **보안:** 대화방 참여자가 아닌 유저는 메시지 내용을 조회할 수 없도록 권한을 검증(Spring Security)합니다.

---

## 3. 시스템 설계서 (SDD)

### 3.1 시스템 아키텍처 (Architecture)

**Hybrid Model (REST + WebSocket)**을 채택합니다.
*   **REST API:** 대화방 목록 로딩, 과거 기록 조회, 파일 업로드 등 **정적 데이터** 처리에 사용합니다.
*   **WebSocket (STOMP):** 실시간 메시지 전달, 읽음 상태 업데이트 등 **실시간 이벤트** 처리에 사용합니다.

```mermaid
[Frontend (React)]
    |
    +-- (REST HTTP) --> [MessageController] --> [MessageService] --> [DB]
    |                        ^
    |                        | (Event Publish)
    +-- (WebSocket) --> [MultiplayerController] --> [SimpMessagingTemplate]
                             |
                             +--> (/topic/chat/room/{roomId}) --> [Subscribers]
```

### 3.2 데이터베이스 모델링 (ERD)

기존의 단순 `Sender -> Receiver` 구조를 **`Room -> Participant -> Message`** 구조로 정규화합니다.

#### 1. ChatRoom (대화방)
| Field | Type | Description |
|:--- |:--- |:--- |
| `id` | Long (PK) | 방 고유 ID |
| `type` | Enum | `DM` (1:1), `GROUP` (그룹) |
| `title` | String | 방 제목 (그룹챗용, DM은 상대방 이름 표시) |
| `last_message` | Text | 목록 표시용 캐싱 (성능 최적화) |
| `updated_at` | DateTime | 정렬용 시간 |

#### 2. ChatParticipant (참여자)
| Field | Type | Description |
|:--- |:--- |:--- |
| `id` | Long (PK) | 고유 ID |
| `room_id` | Long (FK) | 참여 중인 방 |
| `user_id` | Long (FK) | 사용자 ID |
| `joined_at` | DateTime | 입장 시간 (이전 메시지 안 보이게 할 때 사용) |
| `last_read_message_id` | Long | 어디까지 읽었는지 추적 (Cursor) |

#### 3. ChatMessage (메시지)
| Field | Type | Description |
|:--- |:--- |:--- |
| `id` | Long (PK) | 메시지 고유 ID |
| `room_id` | Long (FK) | 소속된 방 |
| `sender_id` | Long (FK) | 보낸 사람 |
| `content` | Text | 내용 |
| `msg_type` | Enum | `TEXT`, `IMAGE`, `SYSTEM` |
| `created_at` | DateTime | 전송 시간 |

### 3.3 API 명세 (API Specifications)

기존 `MessageController`를 리팩토링하여 아래 엔드포인트를 구현합니다.

#### A. REST API (`/api/chat`)

| Method | URI | Description |
|:--- |:--- |:--- |
| **GET** | `/rooms` | 내 대화방 목록 조회 (최신순) |
| **POST** | `/rooms` | 새 대화방 생성 (1:1 또는 그룹) <br> Body: `{ type: "GROUP", inviteUserIds: [1, 2] }` |
| **GET** | `/rooms/{roomId}/messages` | 메시지 내역 조회 (Paging) <br> Query: `?lastId=100&size=20` |
| **POST** | `/rooms/{roomId}/messages` | 메시지 전송 (DB 저장 및 Socket 발행 트리거) |
| **PATCH** | `/rooms/{roomId}/read` | 읽음 처리 (Cursor 업데이트) |
| **POST** | `/rooms/{roomId}/invite` | 그룹 채팅방 초대 |
| **DELETE** | `/rooms/{roomId}/leave` | 방 나가기 |

#### B. WebSocket Topics (STOMP)

| Destination | Description |
|:--- |:--- |
| **Sub** `/topic/chat/room/{roomId}` | 해당 방의 실시간 메시지 수신 |
| **Sub** `/topic/user/{userId}/updates` | 새로운 방 초대 알림, 안 읽은 메시지 전체 카운트 갱신 |
| **Pub** `/app/chat.send` | (선택사항) Socket으로 메시지 전송 시 사용. <br> *REST POST 사용 시 서버 내부에서 처리하므로 불필요할 수 있음.* |

### 3.4 구현 시나리오 (Implementation Flow)

1.  **방 목록 로딩:**
    *   사용자가 메신저 아이콘 클릭 -> `GET /api/chat/rooms` 호출.
    *   각 방의 `roomId`를 기반으로 `/topic/chat/room/{roomId}` 구독(Subscribe) 시작.

2.  **메시지 전송:**
    *   사용자가 입력 후 전송 -> `POST /api/chat/rooms/{roomId}/messages` 호출.
    *   서버: DB에 `ChatMessage` 저장 -> `ChatRoom`의 `last_message` 갱신 -> `SimpMessagingTemplate`으로 `/topic/chat/room/{roomId}`에 브로드캐스팅.

3.  **실시간 수신:**
    *   구독 중인 클라이언트: 소켓 메시지 수신 -> 채팅 UI 리스트에 추가.
    *   보고 있지 않은 클라이언트: `/topic/user/{userId}/updates`를 통해 "새 메시지 알림" 수신 -> 전체 안 읽은 뱃지 숫자 +1.

4.  **읽음 처리:**
    *   사용자가 채팅방을 활성화(Focus)하거나 스크롤 -> `PATCH .../read` 호출.
    *   서버: `ChatParticipant`의 `last_read_message_id` 갱신.

---

## 4. 기존 코드 마이그레이션 가이드

### 4.1 Backend
*   **Entity:** `Message` 엔티티를 `ChatMessage`로 변경하고 `ChatRoom` 관계를 추가해야 합니다.
*   **Controller:** 기존 `MessageController`의 `sendDM` (User-to-User) 로직을 `Room` 기반 로직으로 변경해야 합니다. 하위 호환성을 위해 당분간 유지하되, 내부적으로는 "1:1 Room"을 찾아서 처리하도록 수정 권장.
*   **Service:** `savePlazaMessage` (광장 채팅)와 `saveRoomMessage` (룸 채팅) 로직을 `MessageService` 내에서 명확히 분리.

### 4.2 Frontend
*   `multiplayerService.js`: DM 관련 구독 로직(`onDMMessage`)을 Room 기반 구독(`onRoomMessage`)으로 일반화.
*   UI 컴포넌트: `Messenger` 컴포넌트를 신규 생성하여 `GlobalChat`과 별개로 동작하게 하거나, 탭(Tab) 형태로 통합.
