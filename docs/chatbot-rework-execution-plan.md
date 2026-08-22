# 상담 챗봇 재설계 — 실행계획

> 작성일: 2026-08-22
> 브랜치: `feature/chatbot`
> 목표: 고정형 답변을 상담 세션에서 완전히 떼어내고, 상담 세션은 "상담원 연결" 하나로 한정한다.
> AI는 버튼 유형이 아니라 **운영시간 외 대체 응대**로만 남는다.

## 0. 선행 조건 — dev를 먼저 받는다

`feature/chatbot`의 상담 챗봇 작업은 **이미 dev에 머지됐고**, 이 브랜치는 지금 dev보다 301커밋
뒤에 있다. 그 사이 dev에는 이 계획이 손댈 파일의 후속 변경이 들어갔다.

| dev에만 있는 변경 | 이 계획에 미치는 영향 |
| --- | --- |
| `V41__chat_ai_escalate_notice.sql` (`AI_ESCALATE_NOTICE` 시딩) | 4장의 마이그레이션 번호와 설정 키 정리 범위가 달라진다 |
| `ChatAiAnswerService.appendEscalateNotice` + `ChatConversationMapper.lockById` + `ChatMessageMapper.existsSystemMessage` | 5-3의 "잠금 관련 코드 전부 제거" 경계가 달라진다 — 이관 안내는 남긴다 |
| `ClaudeSupportResponder.answer(conversationId, transcript, aiContext)` 시그니처와 실패 사유별 로깅 | 5-3이 고칠 코드가 지금 파일과 다르다 |
| `AdminChatSettingsPage.tsx` 저장 피드백·요일 채우기 개편 | 6-3의 같은 파일 작업과 정면으로 겹친다 |

~~지금 워킹 트리 기준으로 작업하면 위 네 건을 되돌리는 diff가 만들어진다.~~
**처리됨** — dev를 fast-forward로 머지했다(이 브랜치가 dev보다 앞선 커밋이 없어 충돌 없음).
5장·6장의 대상 코드는 머지 뒤 상태를 기준으로 한다.

## 1. 무엇이 바뀌는가

| 구분 | 현재 | 변경 후 |
| --- | --- | --- |
| 고정형 답변 | 버튼을 누르면 `chat_conversation`이 생기고 답변이 메시지로 저장된다 | 세션·메시지를 만들지 않는다. 답변은 위젯이 즉시 렌더한다. 클릭만 집계 테이블에 남는다 |
| 첫 화면 | 인사말 + 지난 대화 스크롤 + 버튼 | 인사말 + "원하시는 문의 유형을 눌러주세요" + 버튼 + `문의 내역` 진입 버튼 |
| 상담 이력 | 채팅창에 항상 이어 붙어 있다 | `문의 내역` 화면에서만 본다. 여러 상담을 세션 구분 없이 한 스크롤로 잇는다 |
| AI 답변 | 버튼 유형(`answer_type='AI'`)이고 운영시간 외 대화당 3회 한도 | 버튼 유형에서 제거. 상담원 연결 세션이 **운영시간 외**일 때 자동 응대. 한도 없음 |
| AI 답변 후 | 입력 잠김(`AI_ANSWERED`), 상담사 대기열에 노출 | 입력 열림, 대기열에서 제외(`AI_HANDLED`) |
| 운영시간 표시 | 헤더 문장("상담 운영시간이에요") | 초록/회색 점 + 짧은 라벨 |

### 이번 작업에서 제외

- 운영 종료 시각에 미답변 건을 일괄 AI 응답으로 넘기는 배치 (즉시 응답 방식만 채택)
- 상담사 콘솔의 SSE·타이핑·배정 로직 (상태값 이름 교체 외 변경 없음)
- 상담 이력의 무한 스크롤/더 보기 (현행 최근 50건 유지)

## 2. 완료 기준

1. 고정형 버튼을 눌러도 `chat_conversation` / `chat_message`에 행이 생기지 않는다.
2. 고정형 버튼 클릭은 `chat_menu_click`에 적재되고, 운영 설정 화면 지표에서 유형별 클릭 수로 보인다.
3. 상담원 연결로 만들어진 상담만 `문의 내역`에 나타나며, 종료된 상담과 진행 중 상담이 한 스크롤로 이어진다.
4. `answer_type='AI'` 버튼은 생성·수정 화면에서 선택할 수 없고, API가 `AI` 값을 거부한다.
5. 운영시간 외에 상담원 연결 세션에 질문을 보내면 AI가 답하고, 답변 끝에 운영시간 재문의 안내가 붙는다. 이 대화는 대기열(`OPEN`) 건수에 포함되지 않는다.
6. AI가 답한 뒤에도 사용자는 계속 질문할 수 있다. 잠기는 경우는 상담 종료(`CLOSED`) 하나뿐이다.
7. 운영시간 안에는 위젯 헤더에 초록 점이, 밖에는 회색 점이 보인다.
8. 상담사 콘솔에서 AI가 응대한 대화를 별도 필터로 확인할 수 있다.
9. 고정 답변 화면 하단에 `상담원 연결` 버튼이 보이고, 누르면 메뉴 화면으로 되돌아가는 단계 없이 바로 상담 화면으로 넘어간다.
10. 진행 중(종료되지 않은) 상담이 있는 상태에서 그 버튼을 눌러도 상담이 새로 생기지 않고 진행 중인 상담으로 들어간다.

## 3. 상태 모델 정리

```text
  (상담원 연결 클릭)
        │
        ▼
      BOT ──(질문)──▶ WAITING_AGENT ──(상담사 답변)──▶ IN_PROGRESS
                          │                                │
                (운영시간 외: AI 응답)               (재질문) │
                          ▼                                ▼
                     AI_HANDLED ──(재질문)──▶ WAITING_AGENT / AI_HANDLED

  어느 상태에서든 종료하면 CLOSED (유일한 입력 잠금 상태)
```

- `AI_ANSWERED` → **`AI_HANDLED`로 교체.** 이름을 바꾸는 이유는 의미가 반대로 뒤집혔기 때문이다. 기존 값은 "AI가 답했으니 사람이 이어받아야 함 + 입력 잠금"이었고, 새 값은 "자동 응대로 마무리됨 + 사람 답변 대기 아님"이다. 같은 이름을 재정의하면 마이그레이션 이후 코드를 읽는 사람이 옛 주석과 새 동작 사이에서 판단을 잃는다.
- `BOT`을 재사용하지 않는 이유: 고정형이 세션을 만들지 않게 되면 `BOT`은 "연결됐지만 아직 질문 없음" 상태만 남는다. 여기에 AI 응대 완료까지 섞으면, 상담사가 "AI가 밤에 뭐라고 답했는지" 확인할 필터 축이 사라진다.
- `acceptsUserMessage()` → `CLOSED`만 `false`.
- `needsAgentReply()` → `WAITING_AGENT`만 `true`.
- `ChatLockReason` → `CLOSED` 하나만 남김.

## 4. 스키마 — `V46__chat_rework.sql`

`V28~V30`은 이미 머지되었으므로 손대지 않는다. 변경은 전부 V46 한 파일에 담는다.

번호 근거: dev의 마지막 번호는 `V44`이고, `V45`는 `feature/settlement-fair-revenue-summary`가
이미 쓰고 있다. 그 브랜치가 먼저 들어오지 않으면 번호가 한 칸 비는데 Flyway는 이를 문제 삼지
않는다. **다만 머지 직전에 dev의 최신 번호를 한 번 더 확인한다** — 지금 열려 있는 브랜치가
그 브랜치 하나뿐이라는 보장은 없다.

```sql
-- 1) 고정형 버튼 클릭 집계
CREATE TABLE `chat_menu_click` (
    `click_id`   BIGINT   NOT NULL AUTO_INCREMENT,
    `menu_id`    BIGINT   NOT NULL,
    `user_id`    BIGINT   NULL,
    `guest_key`  CHAR(36) NULL,
    `clicked_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`click_id`),
    CONSTRAINT `fk_chat_menu_click_menu` FOREIGN KEY (`menu_id`) REFERENCES `chat_menu` (`menu_id`),
    INDEX `idx_chat_menu_click_menu_time` (`menu_id`, `clicked_at`)
) COMMENT = '고정형 문의 유형 클릭 집계';

-- 2) AI 유형 버튼 정리 (기존 데이터 방어. 시딩에는 AI 유형이 없다)
UPDATE `chat_menu` SET `is_active` = 0 WHERE `answer_type` = 'AI';
ALTER TABLE `chat_menu`
    MODIFY COLUMN `answer_type` VARCHAR(10) NOT NULL COMMENT 'FIXED | AGENT',
    DROP COLUMN `ai_context`;

-- 3) 상태값 교체
UPDATE `chat_conversation` SET `status` = 'AI_HANDLED' WHERE `status` = 'AI_ANSWERED';
ALTER TABLE `chat_conversation`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'BOT'
        COMMENT 'BOT | WAITING_AGENT | AI_HANDLED | IN_PROGRESS | CLOSED (CLOSED만 입력 잠금)',
    MODIFY COLUMN `ai_answer_count` INT NOT NULL DEFAULT 0
        COMMENT 'AI가 답한 횟수. 한도가 아니라 지표용 카운터';

-- 4) 운영 문구
DELETE FROM `chat_setting` WHERE `setting_key` = 'AI_LIMIT_NOTICE';
INSERT INTO `chat_setting` (`setting_key`, `setting_value`, `description`) VALUES
  ('AI_CONTEXT', '', 'AI가 참고할 도메인 지식. 비어 있으면 대부분 상담사에게 넘긴다'),
  ('AI_CLOSING_NOTE', '더 상세한 답변이 필요하시면 운영시간에 다시 문의해주세요.',
   'AI 답변 끝에 서버가 덧붙이는 안내');
UPDATE `chat_setting` SET `setting_value` = '안녕하세요, 펫토피아입니다.\n원하시는 문의 유형을 눌러주세요.'
 WHERE `setting_key` = 'GREETING';
```

- `ai_context`를 메뉴에서 떼어 전역 `chat_setting`으로 옮기는 이유: AI가 더 이상 특정 버튼에 붙지 않는다. 참고 지식이 메뉴에 남아 있으면 "어느 메뉴의 컨텍스트를 쓸 것인가"라는 답 없는 질문이 코드에 남는다.
- `ai_answer_count`는 **삭제하지 않는다.** 한도 집행에서는 빠지지만 "AI가 응대한 대화 수" 지표가 이 값에 걸려 있다. 컬럼 주석으로 용도를 바꿔 적는다.
- `AI_ESCALATE_NOTICE`(dev의 V41)는 **지우지 않는다.** AI가 답을 거부하고 상담사에게 넘기는
  경로는 한도와 무관하게 남는다. 오히려 한도가 사라진 뒤에는 이 문장이 "밤에 답이 안 오는
  이유"를 설명하는 유일한 안내가 된다. 이번에 지우는 설정 키는 `AI_LIMIT_NOTICE` 하나다.
- `chat_menu`의 `ai_context`를 드롭할 때 함께 고쳐야 하는 곳: `ChatMenuMapper.xml`의 컬럼 목록
  7군데(`selectActiveMenus`, `selectActiveByCode`, `selectById`, `selectAllMenus`, `insert`,
  `update`), `ChatMenu` 엔티티, `AdminChatMenuRequest`/`AdminChatMenuResponse`,
  `frontend/src/api/adminChatSettings.ts`. 컬럼 목록을 명시적으로 나열하는 매퍼라 컴파일로는
  걸리지 않고 런타임 `Unknown column`으로 터진다.
- `AI_CLOSING_NOTE`를 프롬프트 지시가 아니라 서버 후처리로 두는 이유: 프롬프트로 부탁하면 붙는 날도 있고 안 붙는 날도 있다. 운영자가 문구를 고칠 수 있어야 한다는 요구까지 겹치면 설정값 + 서버 append가 유일하게 확정적인 방법이다.

## 5. 백엔드 변경

### 5-1. 삭제 / 축소

| 대상 | 처리 |
| --- | --- |
| `ChatAiRateLimiter` | 파일 삭제 (한도 폐지). 대체물은 Redis가 아니라 대화 행의 조건부 UPDATE다 — 5-2-2 |
| `ChatConversationService.AI_ANSWER_LIMIT` | 삭제 |
| `ChatConversationMapper.claimAiAnswer` / `releaseAiAnswer` / `selectAiAnswerCount` | 삭제, `incrementAiAnswerCount`로 대체 |
| `ChatAiAnswerService.releaseSlot`, `ChatAiAnswerListener`의 반납 분기 | 삭제 (반납할 슬롯이 없다) |
| `ChatAiAnswerRequestedEvent.lastAnswer` | 삭제 (마지막 답변 개념이 없다) |
| `ChatAnswerType.AI` | enum 상수 삭제 |
| `ChatLockReason.AI_ANSWERED` | 삭제 |
| 설정 키 `AI_LIMIT_NOTICE` 참조 | 삭제 |
| `ErrorCode.CHAT_AWAITING_AGENT` (CH010, 423) | 삭제. `markWaitingAgent` 실패 사유가 `CLOSED` 하나로 줄면 이 코드는 도달 불가가 된다 |
| `ChatWidget.handleSend`의 423 재조회 분기 | 삭제 (위와 같은 이유). 남겨두면 절대 안 도는 복구 경로를 계속 읽게 된다 |

> **결정됨 — 한도는 전부 제거하고, "대화당 진행 중 호출 1건" 가드만 남긴다.**
>
> 횟수 한도(대화당·요청자당·시간창)는 모두 사라진다. 남기는 것은 한도가 아니라 **중복 제거**다.
> 사용자가 답을 기다리다 같은 질문을 연달아 세 번 보내면 가드 없이는 Claude 호출 3건이 동시에
> 돌고, 세 답변이 순서 없이 한 창에 쌓인다. 앞 답변이 도착하면 다음 질문은 그대로 답을 받으므로
> "한도 없음"과 부딪히지 않는다. 구현은 5-2-2.

### 5-2. `ChatConversationService`

- `start(menuCode, ...)`: 메뉴의 `answerType`이 `AGENT`가 아니면 `CommonException(CHAT_MENU_NOT_CONNECTABLE)`로 거부한다. 프론트가 고정형에 세션을 만들지 않는 것과 별개로, 서버가 단일 판정 주체여야 한다(요청을 직접 만들면 뚫린다).
- `appendOpeningMessages`: `FIXED` 분기 제거. `AGENT_RECEIVED` + (운영시간 외면) `OFFLINE_NOTICE`만 남는다.
- `bootstrap`:
  - `menus`에 `fixedAnswer`를 함께 내린다(고정형만). 클릭 시 추가 요청이 없어야 "누르면 바로 답변"이 성립한다. 고정 답변은 공개 안내문이라 노출 위험이 없다.
  - `history`는 그대로 유지하되, 이제 상담원 연결 세션만 담긴다(고정형이 세션을 만들지 않으므로 자연히 걸러진다).
  - `hasHistory` 플래그를 추가한다. `문의 내역` 버튼의 노출 여부 판단에 쓰고, 프론트가 `history.length`로 재현하지 않게 한다.
- `requestAiAnswerIfEligible` 조건 재정의:
  1. `responder.isEnabled()`
  2. `assignedAdminId == null` (상담사가 개입한 대화에 자동 답변을 끼우지 않는다)
  3. `!businessHourService.isWithinBusinessHours(now)`
  → 메뉴 유형 조건, 요청자 한도, 대화당 선점 전부 제거.
- 신규 `logMenuClick(menuCode, userId, guestKey)`: 활성 메뉴 검증 후 `chat_menu_click`에 한 행 적재. 세션·메시지를 만들지 않는다.

#### 5-2-1. 빠뜨리면 완료 기준 6번이 깨지는 지점

- **`ChatConversationMapper.markWaitingAgent`의 허용 상태 목록에 `AI_HANDLED`를 넣는다.**
  현재 목록은 `('BOT', 'WAITING_AGENT', 'IN_PROGRESS')`다. `acceptsUserMessage()`를 고쳐도 여기를
  같이 고치지 않으면 AI가 답한 대화는 계속 전송이 거부된다 — `ChatConversationService` 클래스
  주석이 "실제 차단은 여기 조건부 UPDATE에서 일어난다"고 못 박아 둔 그 지점이다. enum만 바꾸면
  컴파일은 통과하고 런타임에서 조용히 막힌다.
- `markAiAnswered` → `markAiHandled`로 이름을 바꾸되 **`AND status = 'WAITING_AGENT'` 가드는
  그대로 둔다.** 이 조건은 잠금 장치가 아니라 경쟁 조건 가드다 — 비동기 답변이 도착하는 사이
  상담사가 먼저 답했거나(`IN_PROGRESS`) 사용자가 종료한(`CLOSED`) 대화를 뒤늦게 되돌리지 않기
  위한 것이다. 5-3의 "잠금 관련 코드 제거" 목록에 이 statement를 넣으면 안 된다.

#### 5-2-2. 대화당 진행 중 호출 1건 가드

Redis(`ChatAiRateLimiter`)가 아니라 **대화 행의 조건부 UPDATE**로 만든다. 이 도메인의 판정은
이미 전부 조건부 UPDATE에 있고(`markWaitingAgent`, `markInProgress`), 같은 자리에 두면 선점과
사용자 메시지 커밋이 한 트랜잭션에 묶인다. Redis로 하면 "장애 시 열어줄까 닫을까"라는 답 없는
질문이 생긴다 — 닫으면 야간 자동 응대가 통째로 멈추고, 열면 정작 막으려던 중복이 그대로 난다.

V46에 컬럼 한 개를 더한다.

```sql
ALTER TABLE `chat_conversation`
    ADD COLUMN `ai_call_started_at` DATETIME NULL
        COMMENT '진행 중인 AI 호출 시작 시각. NULL이면 진행 중 아님. 한도가 아니라 중복 방지용';
```

- 선점: `UPDATE ... SET ai_call_started_at = #{now} WHERE conversation_id = ? AND status != 'CLOSED'
  AND (ai_call_started_at IS NULL OR ai_call_started_at < #{staleBefore})`. 반영 행 수가 곧
  "지금 부를 수 있는가"의 답이다.
- 반납: `SET ai_call_started_at = NULL`. 성공·이관·예외·큐 거부 **네 경로 모두**에서 부른다.
  `ChatAiAnswerListener`가 이미 그 조립을 하고 있으니(`answerWithRelease`) 뼈대는 그대로 쓴다.
- `staleBefore = now - 3분`: 프로세스가 죽어 반납이 안 된 행을 스스로 풀어준다. TTL 없는 잠금은
  한 번의 배포 사고로 그 대화의 자동 응대를 영구히 막는다. 3분은 큐 대기(core 2/max 4/queue 50)와
  Claude 호출을 합쳐 넉넉히 잡은 값이고, 이보다 오래 걸린 호출은 중복을 감수하는 편이 낫다.
- 이 가드에 막혀도 **사용자에게 아무 안내도 하지 않는다.** 이미 같은 대화의 답변이 오는 중이므로
  기다리면 도착한다. 여기서 "처리 중이에요"를 붙이면 답변보다 안내가 먼저 쌓인다.

### 5-3. `ChatAiAnswerService`

- `tryAnswer`: 답변 본문 뒤에 `AI_CLOSING_NOTE`를 붙여 한 말풍선으로 저장한다. 그 뒤
  - `conversationMapper.incrementAiAnswerCount(conversationId)`
  - `markAiHandled(conversationId, now)` 성공 시 `publishStatus(AI_HANDLED)`
- 프롬프트 컨텍스트는 이벤트가 실어 오는 메뉴별 값이 아니라 `chat_setting.AI_CONTEXT`에서 읽는다.
- 잠금 관련 코드(`AI_LIMIT_NOTICE` 참조, `ChatLockReason` 분기) 제거. `markAiAnswered`는 제거가
  아니라 `markAiHandled`로의 이름 변경이다(5-2-1).
- 이관/실패 경로(`appendEscalateNotice`, `lockById`, `existsSystemMessage`)는 **유지한다.** 5-1에서
  지우는 것은 한도 관련 코드뿐이다.
- `ChatAiAnswerListener`: 반납할 슬롯이 없어지면 큐가 찼을 때(`RejectedExecutionException`)와
  예상 못한 예외에서 화면에 아무것도 남지 않는다. 반납 대신 이관 안내를 붙인다 — 상태는
  `WAITING_AGENT`로 남아 대기열에 들어가므로 상담은 이어지고, 사용자도 무엇을 기다리는지 알게
  된다. 풀은 core 2 / max 4 / queue 50이라 야간 질문이 몰리면 실제로 닿는 경로다.

### 5-4. 컨트롤러 / API

| 메서드 | 경로 | 비고 |
| --- | --- | --- |
| POST | `/api/chat/menus/{menuCode}/clicks` | 신규. 고정형 클릭 집계. 응답 `204`. 게스트 키 헤더는 선택 |
| POST | `/api/chat/conversations` | `AGENT` 유형만 허용 (그 외 `400`) |
| GET | `/api/chat/bootstrap` | 응답에 `fixedAnswer`, `hasHistory` 추가 |

`ErrorCode`에 `CHAT_MENU_NOT_CONNECTABLE` 추가(다음 빈 코드는 `CH014`).

`ChatAnswerType.AI` 상수를 지우면 `{"answerType":"AI"}` 요청은 Jackson 역직렬화 단계에서 이미
400으로 떨어진다. 완료 기준 4번은 그것으로 충족되지만, 응답 본문은 역직렬화 오류 메시지라
운영자가 읽을 문장이 아니다. 관리자 화면에서 선택 자체가 불가능해지므로(6-3) 별도 처리는 하지
않는다 — 여기 적어두는 이유는 나중에 "왜 메시지가 이상한가"를 다시 조사하지 않기 위해서다.

선택 항목 — `POST /api/chat/conversations`의 `fromMenuCode`(nullable): 고정 답변을 보고 연결한
경우 그 유형 코드를 함께 보낸다. 서버는 상담 시작 시 `SYSTEM` 메시지로 한 줄 남긴다
("'3. 운영관련 문의' 안내를 보고 연결하셨어요."). 상담사가 첫 질문을 받기 전에 무엇을 이미
읽었는지 알 수 있어 같은 안내를 반복하지 않게 된다. 콘솔은 손대지 않아도 된다(메시지로만
전달되므로). 이 항목을 빼도 6-1-1의 나머지는 그대로 성립한다.

### 5-5. 상담사 콘솔 / 운영 설정

- `AdminChatFilter`에 `AI_HANDLED` 추가. `OPEN`은 `WAITING_AGENT`, `IN_PROGRESS`만 (AI 응대 건 제외 — 요구사항의 "상담원이 답변해줘야 할 건으로 잡히지 않는다"가 여기서 집행된다).
- `AdminChatMapper.xml`
  - `statusFilter` / `ORDER BY` / `countWaiting`에서 `AI_ANSWERED` 제거, `AI_HANDLED`는 대기 집계에 넣지 않는다.
  - `selectMenuStats`: `waiting_count` 정의를 `WAITING_AGENT`만으로 좁히고, `chat_menu_click` 파생 테이블을 LEFT JOIN해 `click_count`를 추가한다. 고정형 유형은 `conversation_count`가 0이 되므로 정렬 기준을 `click_count + conversation_count` 합으로 바꾼다.
- `ChatOperationService`: 메뉴 생성·수정에서 `answerType == AI` 거부, `aiContext` 필드 제거. 문구 설정 목록에 `AI_CONTEXT` / `AI_CLOSING_NOTE` 노출.

## 6. 프론트엔드 변경

### 6-1. 위젯 화면 구조

`ChatWidget`에 화면 상태를 둔다: `MENU` → `ANSWER` → (뒤로) / `MENU` → `THREAD`.

```text
[MENU]                         [ANSWER]                      [THREAD]
● 상담 가능 / ○ 운영시간 아님    ← 뒤로   유형 라벨            ← 뒤로  문의 내역
인사말                          ─────────────────            ─────────────────
원하시는 문의 유형을 눌러주세요   고정 답변 본문                 (상담 A 메시지들)
 [1. 참가업체 문의]                                           ── 구분선 ──
 [2. 페어업체 문의]             원하는 답이 아니라면?          (상담 B 메시지들)
 [3. 운영관련 문의]              [상담원 연결]                 ─────────────────
 [4. 길 찾기 문의]                                            입력창 (진행 중일 때)
 ─────────────────                                           / "새 문의하기" (종료 시)
 [상담원 연결]  [문의 내역]
```

- `MENU`: 세션 개념이 없다. 메시지 리스트를 그리지 않는다.
- `ANSWER`: `bootstrap.menus[].fixedAnswer`를 그대로 렌더. 진입 시 클릭 집계 API를 fire-and-forget으로 호출(실패해도 화면에 영향 없음). 하단에 `상담원 연결` CTA를 둔다 — 상세는 6-1-1.
- `THREAD`: 기존 `transcript` + `ChatMessageList`(세션 구분선 포함) 로직을 그대로 옮긴다. SSE·폴백 폴링·타이핑 표시는 이 화면에서만 동작한다 — 지금은 위젯이 열려 있으면 항상 붙는데, 고정 답변만 보는 사용자에게는 불필요한 연결이다.
- 상담원 연결 클릭 시: `startConversation` → `THREAD`로 전환.
- `문의 내역`은 `bootstrap.hasHistory`가 참일 때만 노출.

### 6-1-1. 고정 답변 뒤의 `상담원 연결` 버튼

고정 답변은 대부분의 문의를 끝내지만, 끝내지 못하는 문의가 남는다. 그때 사용자가 해야 하는
행동이 "뒤로 가기를 눌러 메뉴로 돌아가서 다른 버튼을 찾는 것"이면 대부분은 그냥 창을 닫는다.
답변 바로 아래에서 연결되어야 한다.

```text
[ANSWER]
← 뒤로     펫토피아 상담
─────────────────────────────
안녕하세요, 펫토피아입니다.
원하시는 문의 유형을 눌러주세요.

                ┌──────────────────┐
                │ 3. 운영관련 문의  │  ← 누른 버튼 문구 (사용자 말풍선)
                └──────────────────┘
  ┌──────────────────────────┐
  │ 운영시간은 평일 10:00~   │       ← 고정 답변 (상담사 쪽 말풍선)
  │ 18:00이며 주말·공휴일은…  │
  └──────────────────────────┘
─────────────────────────────  ← 입력창 자리에 고정
원하는 답변이 아니었나요?
  [ 상담원에게 직접 문의하기 ]
  지금은 상담사가 확인할 수 있는 시간이에요.      ← 운영시간 안
  운영시간이 아니라 자동 응대로 먼저 답변드려요.  ← 운영시간 밖 (둘 중 하나)
```

**대화처럼 그린다.** 누른 버튼 문구가 사용자 말풍선으로, 저장된 답변이 상담사 쪽 말풍선으로
나온다. 형태를 맞추는 이유는 일관성이다 - 같은 창에서 상담원 연결은 대화로 보이고 고정 답변은
문서로 보이면 사용자는 두 기능이 다른 곳에 있다고 느낀다. 맞춰두면 "물어보면 답이 온다"는
한 가지 사용법만 익히면 된다.

**그래도 서버에는 아무 행도 생기지 않는다.** 완료 기준 1번은 그대로다. 이건 순수한 표현이라
`ChatBubble`에 `ChatMessage`가 아니라 발신자와 본문 두 값만 넘긴다 - 가짜 `messageId`를 만들면
그것이 언젠가 `transcript`에 섞여 서버 커서를 망가뜨린다.

답변 말풍선은 `BOT`으로 그린다. `AGENT`로 두면 사람이 답한 것으로 읽히고, `AI`로 두면
"자동 답변" 배지가 붙어 Claude가 만든 답변과 구분되지 않는다. 둘 다 사실이 아니다.

헤더 제목은 첫 화면과 같은 `펫토피아 상담`이다. 유형은 사용자 말풍선이 이미 말하고 있어
헤더가 되풀이할 이유가 없고, 제목이 그대로면 화면이 바뀐 게 아니라 대화가 이어진 것으로 읽힌다.

상담원 연결은 입력창 자리에 고정한다. 답변이 길어 스크롤이 생겨도 출구가 화면에서 사라지지
않아야 한다 - 스크롤 안에 두면 긴 답변에서는 존재를 모른 채 창을 닫는다.

- **어느 메뉴로 연결하는가.** 프론트가 코드를 하드코딩하지 않는다. `bootstrap.menus`에서
  `answerType === "AGENT"`인 첫 항목(서버가 `display_order`로 정렬해 내려준다)을 대상으로 쓴다.
  시딩값은 `AGENT_CONNECT`("0. 상담사 연결")지만 운영자가 이름·코드·개수를 바꿀 수 있는 데이터라
  코드에 박으면 관리자 화면에서 지우는 순간 버튼이 죽는다. **AGENT 유형이 하나도 없으면 CTA를
  숨긴다** — 이때 `MENU` 화면의 `상담원 연결` 버튼도 같은 판정으로 사라져야 앞뒤가 맞는다.
- **진행 중인 상담이 있으면 새로 만들지 않는다.** `bootstrap.ongoing`이 있고 그 상태가 `CLOSED`가
  아니면 `startConversation`을 부르지 않고 그 대화의 `THREAD`로 들어간다. 이 분기가 없으면,
  답변을 기다리는 중에 고정 답변을 눌러본 사용자가 CTA를 누르는 순간 대기열에 같은 사람의 상담이
  두 건 뜬다. 상담사는 그게 같은 사람인지 알 수 없다.
- **핸들러는 `MENU` 화면의 `상담원 연결`과 공유한다.** 같은 동작이 두 곳에서 시작되는데 코드가
  갈라지면 위의 두 규칙 중 하나만 반영되는 사고가 난다.
- **운영시간 힌트를 버튼 아래에 붙인다.** 6-2의 헤더 점과 같은 `withinBusinessHours` 값을 쓰되
  문장은 다르다 — 헤더는 상태 표시이고, 여기는 "누르면 어떻게 되는지"의 예고다. 밤에 눌러
  자동 응대를 받은 사용자가 "사람이 아니었다"고 느끼지 않게 하는 것이 목적이다.
- 전환 중에는 버튼을 `disabled`로 두고(중복 생성 방지), 실패하면 화면을 바꾸지 않고 버튼 옆에
  오류를 남긴다. `ANSWER`를 벗어난 뒤 실패하면 사용자는 방금 읽던 답변을 잃는다.

### 6-2. 운영시간 표시

헤더에 `<span aria-hidden>` 점 + 텍스트 라벨을 함께 둔다(색만으로 정보를 전달하지 않는다).

- 운영시간: 초록 점 + "상담 가능 · 18:00까지"
- 그 밖: 회색 점 + "운영시간 아님 · 자동 응대"

**종료 시각을 함께 붙이는 이유.** "상담 가능"만으로는 "지금 물어봐도 되나"까지만 알 수 있고
"얼마나 여유가 있나"는 알 수 없다. 17:55에 문의를 시작하는 사람과 10시에 시작하는 사람은
기대가 달라야 한다. 같은 값을 고정 답변 화면의 연결 버튼 아래에도 쓴다
("18:00까지 상담사가 확인할 수 있어요").

`bootstrap.closesAt`은 **운영시간 안일 때만 채운다.** 밖에서도 내려보내면 "닫혀 있는데 종료
시각을 보여주는" 화면이 만들어진다. 판정과 조회는 같은 `now`로 묶는다 - 서로 다른 시각을 보면
경계(18:00)에서 "상담 가능 · 18:00까지"와 "운영시간 아님"이 뒤섞인다.

밖에서 다음 여는 시각을 주지 않는 이유: 요일을 넘겨가며 휴무일까지 건너뛰어 다음 운영일을
찾는 별개의 계산이고, 그 시간대에는 자동 응대가 먼저 답하므로 사용자가 기다릴 이유가 없다.

### 6-3. 파일별 작업

| 파일 | 작업 |
| --- | --- |
| `api/chat.ts` | `ChatAnswerType`에서 `AI` 제거, `ChatLockReason`을 `"CLOSED"`로, `ChatMenu.fixedAnswer` / `ChatBootstrap.hasHistory` 추가, `logMenuClick()` 추가 |
| `components/chat/ChatWidget.tsx` | 화면 상태 도입, 고정형 분기 제거, SSE/폴링을 `THREAD` 한정, 헤더 상태 점 |
| `components/chat/ChatMenuButtons.tsx` | 고정형/연결 버튼 시각 구분, `문의 내역` 버튼 수용 |
| `components/chat/FixedAnswerView.tsx` | 신규. 인사말 + 사용자/답변 말풍선 + 하단 고정 CTA(6-1-1). 연결 대상 판정과 진행 중 상담 분기는 `ChatWidget`이 갖고 콜백만 받는다 |
| `components/chat/ChatBubble.tsx` | 신규. `ChatMessageList`에서 떼어낸 표현 전용 말풍선. 고정 답변 화면과 실제 상담이 같은 모양을 쓰게 한다 |
| `components/chat/ChatComposer.tsx` | 잠금 사유가 `CLOSED` 하나로 줄어든 문구 정리 |
| `pages/admin/AdminChatPage.tsx` | 필터 탭에 `AI 응대` 추가, 상태 라벨 교체 |
| `pages/admin/AdminChatSettingsPage.tsx` | `answerType` 옵션에서 AI 제거, 메뉴별 `AI 참고 정보` 입력 제거, 전역 문구 편집에 `AI_CONTEXT`/`AI_CLOSING_NOTE`, 지표 표에 `클릭 수` 컬럼 |

## 7. 작업 순서

1. **스키마** — `V46__chat_rework.sql` 작성, 로컬 마이그레이션 검증.
2. **백엔드 상태 모델** — enum(`ChatAnswerType`, `ChatConversationStatus`, `ChatLockReason`), 매퍼 statement 교체. 이 단계에서 컴파일이 깨지는 지점이 곧 영향 범위 목록이 된다.
3. **백엔드 AI 경로** — 한도 관련 코드 제거, `AI_CONTEXT`/`AI_CLOSING_NOTE` 반영, `AI_HANDLED` 전이.
4. **백엔드 API** — 클릭 집계 엔드포인트, `start()` 유형 제한, bootstrap 응답 확장.
5. **관리자 측** — 대기열 필터·지표 쿼리·운영 설정 서비스.
6. **프론트 위젯** — 화면 분리, 고정 답변 뷰, 상태 점, `문의 내역`.
7. **프론트 관리자** — 설정/콘솔 화면 정리.
8. **테스트 및 수동 검증.**

각 단계는 독립 커밋으로 나눈다. 2번을 먼저 통과시키면 이후 단계에서 상태값 혼용 실수가 컴파일 단계에서 걸린다.

## 8. 테스트

~~현재 `src/test`에는 상담 관련 테스트가 하나도 없다.~~ **작성됨** — 39건(`./gradlew test
--tests "com.ms.petopia.api.chat.*"` 실행 건수).

| 클래스 | 건수 | 무엇을 지키는가 |
| --- | --- | --- |
| `ChatConversationServiceTest` | 12 | 고정형이 상담을 못 만드는가, 클릭이 세션을 안 만드는가, AI 예약 조건 |
| `ChatAiAnswerServiceTest` | 6 | 마무리 안내가 한 말풍선인가, 전이 실패 시 아무것도 저장하지 않는가, 이관 안내 중복 |
| `ChatConversationStatusTest` | 12 | 잠금·대기 판정 규칙 자체(`@EnumSource`로 상태마다 한 건씩 돈다) |
| `ChatMapperSqlContractTest` | 6 | **enum과 SQL의 상태 목록이 어긋나지 않는가** |
| `ChatOperationServiceTest` | 3 | 고정 답변 유형이 본문 없이 저장되지 않는가 |

`ChatMapperSqlContractTest`가 이 재설계에서 가장 값이 나가는 테스트다. 기대값을 문자열로 적지
않고 enum에서 뽑아내므로, 상태를 하나 추가하면 이 테스트가 먼저 깨지면서 "그 상태를 SQL 목록에
넣을지"를 결정하게 만든다. 5-2-1의 버그를 실제로 잡는지 확인했다 — `markWaitingAgent`에서
`AI_HANDLED`를 지우면 그 테스트만 붉어진다.

### 단위 / 통합

클래스별 실제 케이스다. 위 표의 건수와 이 목록이 어긋나면 목록이 낡은 것이다.

- `ChatConversationServiceTest` (12)
  - 고정형 코드로 `start()` 호출 → `CHAT_MENU_NOT_CONNECTABLE`
  - bootstrap은 운영시간 안에서만 종료 시각을 준다. 밖에서는 null이고 조회 자체를 하지 않는다 (2건)
  - `logMenuClick` → 세션·메시지 생성 없음, 클릭 행 1건
  - 내려간 버튼의 클릭 → `CHAT_MENU_NOT_FOUND`, 클릭 행 없음
  - 운영시간 내 질문 → AI 이벤트 미발행, 상태 `WAITING_AGENT`
  - 운영시간 외 질문 → AI 이벤트 발행
  - 같은 대화에서 운영시간 외 질문 5회 반복 → 5회 모두 이벤트 발행 (한도 없음 확인)
  - 진행 중 호출이 있는 대화의 추가 질문 → 이벤트 미발행 (5-2-2)
  - 선점 요청의 stale 기준 시각이 현재보다 과거다
  - 상담사 배정된 대화 → 이벤트 미발행, `claimAiCall`도 부르지 않는다
  - 종료된 대화 → `CHAT_ALREADY_CLOSED`
- `ChatAiAnswerServiceTest` (6)
  - 답변 본문 끝에 `AI_CLOSING_NOTE`가 같은 말풍선으로 붙는다
  - 성공 시 상태 `AI_HANDLED`, `ai_answer_count` 증가
  - **전이 실패 시 말풍선·지표·상태 이벤트 어느 것도 남지 않는다** (12장)
  - 이관(빈 답변) 시 `AI_ESCALATE_NOTICE`가 붙고 상태는 `WAITING_AGENT`로 남는다
  - 같은 이관 안내는 다시 붙지 않는다(행 잠금 뒤 확인)
  - `releaseCall`이 선점을 반납한다
- `ChatConversationStatusTest` (12) — `acceptsUserMessage()`는 `CLOSED`만 거짓, `needsAgentReply()`는
  `WAITING_AGENT`만 참, `AI_HANDLED`는 잠기지 않는다, 답변 유형에 `AI`가 없다
- `ChatMapperSqlContractTest` (6) — `markWaitingAgent` 허용 목록(5-2-1) / `countWaiting` 대기 정의 /
  `OPEN` 필터에 `AI_HANDLED` 없음 / `markAiHandled`의 `WAITING_AGENT` 가드 / `claimAiCall`이 횟수가
  아니라 stale로 판정 / 옛 상태값 잔존 없음
- `ChatOperationServiceTest` (3) — `FIXED`는 빈 본문으로 생성·수정 모두 거부, `AGENT`는 빈 본문 허용

### 수동 검증

1. 위젯을 열면 인사말 + 버튼만 보이고, 메시지 영역이 없다.
2. 고정형 버튼 → 즉시 답변, 새로고침 후 `문의 내역`에 그 답변이 없다.
2-1. 고정 답변 화면의 `상담원 연결` → 메뉴를 거치지 않고 상담 화면으로 넘어가고, 콘솔 대기열에 한 건만 뜬다.
2-2. 상담 진행 중에 고정 답변을 열고 `상담원 연결`을 눌러도 대기열 건수가 늘지 않는다.
2-3. 관리자 화면에서 AGENT 유형 버튼을 전부 비활성화하면 고정 답변 화면과 메뉴 화면에서 `상담원 연결`이 함께 사라진다.
3. 상담원 연결 → 질문 → 콘솔 대기열에 노출 → 상담사 답변이 위젯에 즉시 도착.
4. 운영시간 밖으로 서버 시간을 옮기고 질문 → AI 답변 + 재문의 안내가 한 말풍선에 붙는다. 콘솔 `OPEN` 건수는 늘지 않고 `AI 응대` 탭에 보인다.
5. AI 답변 후 입력창이 열려 있고 추가 질문이 정상 전송된다.
6. 상담 종료 후 입력창이 잠기고, `문의 내역`에는 종료된 상담이 남는다.
7. 운영시간 안/밖에서 헤더 점 색과 라벨이 바뀐다.

### 남은 것

- 수동 검증(아래 1~7, 2-1~2-3)은 아직 돌리지 않았다. 로컬 DB가 V27 미적용 상태로 드리프트돼
  있어 앱이 뜨지 않는다 - Flyway가 "resolved migration not applied: 27"로 막는다. 이 브랜치와
  무관한 선행 상태이고(머지 이전 커밋에서도 같은 7건이 실패한다), 로컬 스키마를 다시 만들면
  풀린다. 컨텍스트를 띄우는 통합 테스트 7건이 같은 이유로 실패한다.
- 상담사 콘솔의 `AI 응대` 탭은 서버 필터·프론트 탭까지 붙였지만 실제 데이터로 확인하지 않았다.
- 아직 없는 테스트 두 건: `AdminChatService`의 `OPEN` 필터·`countWaiting`을 서비스 레벨에서 보는
  것과, `selectMenuStats`가 대화 0건인 고정형에도 클릭 수를 채우는지 보는 매퍼 테스트. 앞은
  `ChatMapperSqlContractTest`가 SQL 모양으로, 뒤는 10장의 지표 표 확인이 각각 대신하고 있다.

## 11. 관리자 화면 점검 (2026-08-22)

재설계가 관리자 쪽에 남긴 어긋남을 훑어 여섯 건을 고쳤다.

| # | 문제 | 왜 문제인가 | 처리 |
| --- | --- | --- | --- |
| 1 | 안내 문구에 "AI 답변은 운영시간 외에 대화당 1회만" / "AI 유형 문의는 AI가 먼저 1회 답변" | AI 유형도, 1회 한도도 없어졌다. 운영자가 없는 기능을 찾게 된다 | 문구 교체 |
| 2 | 새 버튼 추가 폼에 고정 답변 입력란이 없다 | `FIXED` 버튼이 **항상 빈 답변으로** 만들어진다. 재설계 전에는 빈 메시지가 남았지만 지금은 눌러도 빈 말풍선만 나오는 죽은 버튼이고, 상담사 쪽에 흔적조차 없어 실수를 알아챌 길이 클릭 지표뿐이다 | 입력란 추가 + 저장/추가 차단 + 서버 검증(`CH015`) |
| 3 | 마지막 `AGENT` 버튼을 내려도 아무 경고가 없다 | 위젯에서 사람에게 문의할 경로가 통째로 사라지는데(6-1-1의 의도된 동작), 운영자는 버튼 하나를 내렸다고만 안다 | 활성 `AGENT`가 없으면 경고 배너 |
| 4 | `AGENT`로 바꾼 버튼의 고정 답변이 공개 API에 계속 실린다 | 컬럼에는 예전 값이 남는데 `ChatMenuResponse`가 그대로 내려, 화면이 쓰지도 않는 본문이 공개 응답에 실린다 | `FIXED`일 때만 내린다 |
| 5 | 콘솔 헤더의 "답변을 기다리는 상담 N건"이 현재 탭·페이지 기준 | 페이지 크기(20)를 넘으면 잘리고, `AI 응대`·`종료` 탭에서는 항상 0이 된다. 탭에 따라 달라지는 숫자는 신뢰할 수 없다 | 이미 있던 `/unanswered-count`를 쓴다(프론트가 한 번도 부르지 않고 있었다) |
| 6 | `AI_CONTEXT` 입력란이 2줄, 지표 표 `key`가 버튼 문구 | 문단을 적는 자리인 줄 모르고 한 문장만 적으면 AI가 대부분 이관한다. 같은 문구의 버튼 두 개를 만들면 React key가 겹친다 | 8줄로 확대, key에 순번 결합 |

2·3·5는 이 재설계가 만든 문제고, 4·6은 그 전부터 있었지만 재설계로 실제 증상이 생겼다.

## 9. 리스크

| 리스크 | 대응 |
| --- | --- |
| AI 한도 제거로 Claude 호출 비용이 예측 불가능해진다 | 5-1의 확인 항목. 최소 방어를 넣을지 배포 전 결정한다 |
| `AI_ANSWERED` 문자열이 코드·주석·프론트 타입에 흩어져 있다 | 2단계에서 enum 먼저 교체해 컴파일 오류로 전수 검출. 문자열 리터럴은 `grep -rn "AI_ANSWERED"`로 최종 확인 |
| 이미 `AI_ANSWERED`로 잠긴 운영 대화가 있으면 마이그레이션 후 잠금이 풀린다 | 의도한 동작. 잠금 해제 방향이라 사용자가 손해 보는 전이가 아니다 |
| 고정 답변을 bootstrap에 전부 실으면 응답이 커진다 | 현재 버튼 4~5개, 답변 각 200자 내외로 무시할 수준. 버튼이 수십 개로 늘면 클릭 시 조회로 되돌린다 |
| 고정형 클릭 로그가 봇 트래픽으로 부풀 수 있다 | 지표 전용 테이블이라 오염되어도 상담 데이터에 영향 없음. 필요해지면 IP/게스트 키 단위 중복 제거를 지표 쿼리에서 처리 |
| dev에 이미 들어간 후속 chat 변경을 되돌리는 diff가 만들어진다 | 0장. 스키마 작업 전에 dev를 받고 5·6장 대상 코드를 다시 확인한다 |
| `acceptsUserMessage()`만 고치고 `markWaitingAgent`의 상태 목록을 놓친다 | 5-2-1. enum이 아니라 SQL이 실제 판정 주체다. 8장에 이 한 줄을 겨냥한 테스트를 넣었다 |
| 고정 답변 CTA가 연결 대상 메뉴를 못 찾아 죽은 버튼이 된다 | 6-1-1. 코드 하드코딩 대신 `answerType === "AGENT"` 조회, 없으면 숨김. 수동 검증 2-3 |
| 한 사용자가 야간에 질문을 연달아 보내 Claude 호출이 동시에 여러 건 돈다 | 5-1의 권고(대화당 진행 중 1건)를 받아들이면 해소된다. 받아들이지 않으면 큐(50) 포화 시 이관 안내로 흘린다(5-3) |

## 10. 수동 검증 결과 (2026-08-22)

로컬 `petopia_db`가 V27 미적용 상태로 드리프트돼 있어 그 스키마는 손대지 않고, 별도 스키마
`petopia_verify`에 앱을 띄워 검증했다. **V1→V46 42개 마이그레이션이 한 번에 깨끗히 적용됐다** —
4장을 부분 체인(V28→V30→V41→V46)으로만 확인했던 것보다 강한 검증이다.

| 항목 | 결과 | 확인 방법 |
| --- | --- | --- |
| 1. 위젯에 인사말 + 버튼만, 메시지 영역 없음 | 통과 | 브라우저 |
| 2. 고정형 → 즉시 답변, 상담 생성 없음 | 통과 | 브라우저 + `chat_conversation` 0건 |
| 2-1. 고정 답변의 `상담원 연결` → 메뉴 경유 없이 상담 화면 | 통과 | 브라우저 |
| 2-2. 진행 중 상담에서 다시 눌러도 대기열 안 늘어남 | 통과 | 상담 수 8 → 8 |
| 2-3. AGENT 유형 전체 비활성 → 두 곳의 연결 버튼 동시 소멸 | 통과 | DOM 확인 |
| 3. 상담원 연결 → 질문 → 대기열 노출 | 통과 | 관리자 API `OPEN` |
| 4. 운영시간 외 AI 답변 + 재문의 안내 | **미검증** | `ANTHROPIC_API_KEY` 없음 |
| 5. AI 답변 후 입력 열림, 추가 질문 전송됨 | 통과 | `AI_HANDLED` → 전송 → `WAITING_AGENT` |
| 6. 종료 후 입력 잠김, 이력 유지 | 통과 | `lockReason=CLOSED`, CH011, history 4건 |
| 7. 운영시간 안/밖 점 색·라벨 | 통과 | 초록 `#79C99A` / 회색 `#777` |
| 8. 콘솔 `AI 응대` 탭 분리 | 통과 | `OPEN`=2, `AI_HANDLED`=1, 배지=1 |

추가로 확인한 것.

- **진행 중 호출 1건 가드**를 SQL로 직접 돌려 네 경우를 확인했다: 1회차 선점 성공 / 2회차 차단 /
  반납 후 재선점 성공 / 3분 넘은 stale 선점 재선점 성공 / 종료된 대화 차단.
- **지표 표**: 고정형은 접수 0 · 클릭 3·1·1로 나오고, 정렬이 클릭+접수 합으로 동작한다.
- **상담사 이어받기**: `AI_HANDLED` 대화에 상담사가 답하면 `IN_PROGRESS`로 넘어가고 배지는 안 늘어난다.

### 검증 중 발견해 고친 것

1. **`SUM()`이 NULL로 나오던 지표 컬럼.** 고정형은 대화가 0건이라 `SUM(...)`의 입력 행이 없고,
   그때 SUM은 0이 아니라 NULL이다. DTO가 `long`이라 매핑은 0으로 흘러가지만 SQL이 "행이 없으면
   0"을 말하게 `COALESCE`로 감쌌다.
2. **`문의 내역` 버튼이 안 보이던 문제.** `bootstrap.hasHistory`만 보면 그 값은 패널을 열 때 받은
   스냅샷이라, 이 세션에서 상담을 시작한 경우 여전히 거짓이다. 상담을 만든 뒤 뒤로 나오면 자기
   상담으로 돌아갈 길이 사라졌다 - `상담원 연결`이 진행 중 상담으로 데려가긴 하지만 그 라벨은
   "돌아가기"로 읽히지 않는다. `transcript.length > 0`을 함께 본다.

### 4번을 못 돌린 이유와 남은 확인

`ANTHROPIC_API_KEY`가 로컬에 없어 `ClaudeSupportResponder.isEnabled()`가 거짓이고, 그러면 선점도
이벤트도 발생하지 않는다. 키가 있는 환경에서 아래 세 줄만 확인하면 된다.

1. 운영시간 외 질문 → AI 말풍선 하나에 답변 + `AI_CLOSING_NOTE`가 함께 붙는가
2. 그 대화 상태가 `AI_HANDLED`이고 콘솔 `OPEN` 건수는 늘지 않는가
3. 같은 대화에 연달아 두 번 질문했을 때 답변이 하나만 오는가(진행 중 호출 가드)

1·2는 단위 테스트가, 3은 SQL 직접 검증이 각각 덮고 있어 회귀 위험은 낮다.

## 12. PR 리뷰 반영 (2026-08-22)

PR #226에 붙은 자동 리뷰 10건(인라인 7 · diff 밖 1 · nitpick 2)을 코드와 대조했다. 고친 것과
고치지 않기로 한 것을 함께 남긴다 - 뒤쪽이 더 중요하다. 같은 지적이 다음 리뷰에도 올라온다.

| 지적 | 판단 | 처리 |
| --- | --- | --- |
| AI 답변을 붙이기 **전에** `markAiHandled`를 통과해야 한다 | 유효 | 전이를 저장 권한으로 쓴다. 0을 받으면 말풍선·지표·상태 이벤트 전부 남기지 않고 로그만 남긴다 |
| 선점(`claimAiCall`)과 상담사 배정이 원자적으로 배타가 아니다 | 유효 | `assigned_admin_id IS NULL`을 UPDATE 조건으로 옮겼다. 서비스의 사전 검사는 트랜잭션 시작 시점의 행을 보므로 배타를 집행하지 못한다 |
| 콘솔의 목록과 미답변 건수를 `Promise.all`로 묶었다 | 유효 | 각각 반영한다. 건수 조회가 실패해도 대기열은 갱신되고, 답변·종료 직후에도 같은 함수로 배지까지 맞춘다 |
| 전송 실패 후 대화 상태를 다시 맞추지 않는다 | 유효 | 실패 경로에서 `syncFromServer` 한 번. `CHAT_ALREADY_CLOSED`에 입력창이 열린 채 남지 않는다 |
| 진행 중 상담인데 헤더 제목이 `문의 내역` | 유효 | 진행 중이면 `상담 진행 중`. 입력창이 뜨는 조건을 제목도 따른다 |
| 테스트 건수 34 ↔ 39 불일치 | 유효 | 8장 표를 실제 실행 건수로 맞추고, 없는 테스트 두 건을 「남은 것」으로 내렸다 |
| `closesAt` 주석이 `"HH:mm:ss"` | 유효 | 서버는 ISO로 내려주고 초가 0이면 생략한다(`"18:00"`). 주석만 고쳤다 - `slice(0, 5)`는 양쪽 다 맞다 |
| 클래스 전체 `Strictness.LENIENT` | 유효 | 세 테스트 클래스에서 제거. 레포의 다른 74개 Mockito 테스트와 같은 기준이 됐고, 그 즉시 미사용 스텁 한 건(배정 검사가 운영시간 판정보다 앞이라 소비되지 않던 스텁)이 잡혔다 |
| 클릭 집계를 `FIXED`로 제한하라 | **반영 안 함** | 전제가 틀렸다. `상담원 연결` 버튼의 클릭도 의도적으로 집계한다(위젯이 부른다). 접수 수와 나란히 놓으면 "누르고 그만둔 비율"이 보이고, 막으면 그 지표가 사라지면서 연결 클릭마다 4xx가 나간다 |
| `markInProgress`가 진행 중 AI 선점을 거부해야 한다 | **반영 안 함** | 상담사의 인수는 항상 이겨야 한다. AI 호출이 도는 동안 답변이 실패하면 콘솔이 막힌다 |
| 선점에 호출별 소유자 토큰을 둬라 | **반영 안 함** | 컬럼과 이벤트 필드를 늘려 남는 손해가 Claude 호출 1건이다. 겹친 두 작업이 답변을 둘 다 저장하는 일은 위 첫 항목이 막는다 - 뒤에 도착한 쪽은 전이에서 0을 받고 자기 답변을 버린다 |
