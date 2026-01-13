# 실시간 타이핑 인디케이터 최적화 가이드 (Typing Indicator Logic)

이 문서는 실시간 채팅 시스템에서 네트워크 부하를 줄이면서도 정확하게 상대방의 입력 상태를 보여주는 **"상태 변화 기반(State-Change Based)"** 전송 로직을 설명합니다.

---

## 1. 핵심 개념: 성능 vs. 반응성

채팅창에서 사용자가 글을 칠 때마다 서버에 "나 입력 중이야"라고 신호를 보내는 방식(Event-based)은 네트워크에 큰 부담을 줍니다. 본 가이드는 **상태가 변하는 순간에만** 데이터를 보내는 방식을 제안합니다.

### 비교:
- **전통적 방식**: 키보드를 누를 때마다(KeyUp/KeyDown) 메시지 전송 → 초당 수십 번의 패킷 발생.
- **최적화 방식**: '미입력'에서 '입력 중'으로 변하는 **최초 1회**만 전송 → 트래픽 90% 이상 감소.

---

## 2. 시스템 플로우 (System Flow)

타이핑 상태는 다음의 3단계 흐름을 따릅니다.

1.  **감지 (Detection)**: 프론트엔드에서 입력창의 텍스트 유무를 실시간으로 확인.
2.  **필터링 (Filtering)**: 현재 상태와 이전 상태를 비교하여 **상태가 바뀐 경우에만** 서버로 전송.
3.  **해제 (Release)**: 텍스트가 사라지거나 메시지가 전송되면 즉시 '중단' 신호 전송.

---

## 3. 상세 로직 및 구현 가이드

### A. 클라이언트 (Frontend) 로직
핵심은 **`isTyping` 상태를 추적하는 Reference(참조값)**를 사용하는 것입니다.

```javascript
// 핵심 로직 의사코드 (Pseudo-code)

let isTypingRef = false; // 현재 내가 '입력 중' 신호를 보냈는지 여부

function handleInputChange(event) {
    const text = event.target.value;
    const shouldBeTyping = text.length > 0; // 글자가 있으면 true

    // 중요: 현재 상태가 이전 전송 상태와 다를 때만 전송
    if (shouldBeTyping !== isTypingRef) {
        isTypingRef = shouldBeTyping; // 상태 업데이트
        sendToWebSocket(shouldBeTyping); // 서버로 TRUE 또는 FALSE 전송
    }
}

function handleMessageSend() {
    // 메시지를 보내면 강제로 상태 해제
    isTypingRef = false;
    sendToWebSocket(false);
}
```

### B. 전송 트리거 (Trigger Points)
| 상황 | 데이터 (`isTyping`) | 설명 |
| :--- | :--- | :--- |
| **최초 입력 시작** | `true` | 글자 수가 0 → 1이 되는 시점에 전송 |
| **입력 중 (연속)** | - | 전송하지 않음 (이전 상태가 이미 `true`이므로) |
| **전체 삭제** | `false` | 글자 수가 N → 0이 되는 시점에 전송 |
| **메시지 전송** | `false` | 엔터나 전송 버튼 클릭 시 즉시 전송 |
| **채팅방 퇴장** | `false` | (권장) 창을 닫을 때 '중단' 신호를 보내 매너 유지 |

---

## 4. 백엔드 브로드캐스트 (Backend Broadcast)

백엔드는 수신한 타이핑 데이터를 해당 채팅방의 **나를 제외한 다른 참여자들**에게만 배달합니다.

- **받는 데이터**: `{ "userId": 1, "roomId": 10, "isTyping": true }`
- **전달 방식**: 펍/섭(Pub/Sub) 모델을 사용하여 실시간 브로드캐스트.

---

## 5. 도입 시 장점

1.  **네트워크 비용 절감**: 불필요한 WebSocket 패킷 전송을 차단하여 서버 부하를 최소화합니다.
2.  **정확한 UX**: 타이머(3초 대기 등) 방식보다 사용자의 행위를 더 즉각적이고 정확하게 반영합니다.
3.  **구현의 단순함**: 복잡한 `setTimeout`이나 `clearTimeout` 관리 없이 텍스트 길이만으로 제어가 가능합니다.

---
> [!TIP]
> **모바일 환경**에서는 네트워크가 불안정할 수 있으므로, 해당 로직을 적용하면 배터리 소모와 데이터 사용량을 줄이는 데 더욱 효과적입니다.
