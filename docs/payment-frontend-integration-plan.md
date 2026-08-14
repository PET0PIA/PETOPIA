# 예약금 결제 프론트엔드 연동 실행계획 (토스페이먼츠)

예약 → 결제를 화면에서 완결시키기 위해 프론트엔드에 한 작업의 실행계획이다.
**프론트엔드 변경만 다룬다** — 백엔드 결제/예약 API는 이미 있던 것을 그대로 쓴다.

---

## 0. 결론부터

작업 전에는 유료 예약을 **화면에서 완료할 방법이 아예 없었다.**

- `TicketReservationPage`의 결제 단계가 "결제 기능 준비 중" 플레이스홀더였다
- 유료 사전예약은 **예약 생성 API조차 호출하지 않고** 결제 단계로 넘어갔다
- 토스 SDK 미설치, 클라이언트 키를 프론트로 전달할 경로 없음
- 결제창이 돌아올 `successUrl`/`failUrl` 라우트가 없어서 승인 확정(`confirm`)을 부를 시점이 없었음

이 문서의 작업을 마치면 `예약 생성 → 결제 생성 → 토스 결제창 → 승인 확정 → 입장 QR`이 브라우저에서 끝난다.

### 변경 파일

| 파일 | 성격 |
|---|---|
| `frontend/package.json` | `@tosspayments/tosspayments-sdk` 추가 |
| `frontend/.env` / `.env.example` | `VITE_TOSS_CLIENT_KEY` |
| `frontend/src/payments/toss.ts` | **신규** — SDK 로더 + 결제창 호출 |
| `frontend/src/pages/payment/PaymentResultPage.tsx` | **신규** — success/fail 착지 페이지 |
| `frontend/src/api/payment.ts` | 결제자 ID 기본값 제거 |
| `frontend/src/api/reservation.ts` | `ADVANCE_TERMS_VERSION` 추가 |
| `frontend/src/pages/reservation/TicketReservationPage.tsx` | 결제 단계 실제 구현 |
| `frontend/src/router/AppRouter.tsx` | 결제 결과 라우트 2개 |

---

## 1. 설계 결정

### 1-1. 결제위젯 vs 결제창 → **결제창(`payment.requestPayment`)**

토스 SDK는 두 방식을 제공한다.

| | 결제위젯 `widgets` | 결제창 `payment` |
|---|---|---|
| 사전 준비 | 토스 어드민에 **위젯 설정 등록 필요** | 클라이언트 키만 있으면 됨 |
| UI | 결제수단 UI를 우리 DOM에 렌더 | 토스가 띄우는 창 |
| 키 종류 | **결제위젯 연동 키** | **API 개별 연동 키** |

연동 초기에는 변수를 줄이는 게 우선이라 **결제창**을 골랐다. 위젯은 어드민 설정이 안 돼 있으면 초기화 단계에서 실패하는데, 그게 우리 코드 문제인지 설정 문제인지 구분하기 어렵다.

> **함정**: 두 방식은 **클라이언트 키 종류가 다르다.** 위젯용 키를 `payment()`에 넣으면 SDK가 초기화에서 거부한다. 위젯으로 바꾸려면 키도 같이 바꿔야 한다.

### 1-2. 리다이렉트 vs Promise → **리다이렉트**

`requestPayment`에 `successUrl`을 주면 리다이렉트 방식, 안 주면 Promise 방식이다. 리다이렉트를 골랐다. 결제창에서 앱 전환(간편결제 등)이 일어나면 Promise 방식은 컨텍스트가 끊길 수 있고, 리다이렉트는 결과가 URL에 남아 재현·디버깅이 쉽다.

**대신 리다이렉트는 앱을 완전히 새로 띄운다.** 메모리에만 있던 access token이 사라지고 `AuthProvider`의 토큰 재발급(silent refresh)으로 복구되므로, 결과 페이지는 **인증 복구가 끝난 뒤에** confirm을 불러야 한다(§6-1). 이걸 빠뜨리면 **결제는 승인됐는데 confirm을 못 불러 예약이 확정 안 되는** 최악의 상태가 된다.

### 1-3. 금액·주문번호는 서버 값을 그대로 쓴다

백엔드 `PaymentService.confirmPayment`는 승인 요청에 **자기 DB의 금액**과 `"PAYMENT_" + paymentId`로 만든 `orderId`를 쓴다. 프론트가 다른 값을 결제창에 넘기면 토스가 4xx로 거절한다.

→ 결제 생성 응답의 `orderId`, `amount`를 **가공 없이** 결제창에 전달한다. 화면에 보여준 예약금(`reservationFee`)이 아니라 **예약 생성 응답의 `amount`** 를 쓴다.

### 1-4. 결제 실패·이탈 시 예약은 건드리지 않는다

예약을 `PENDING_PAYMENT`로 두고 안내만 한다. 제한시각이 지나면 서버의 만료 배치가 정리하고, 그 전까지는 사용자가 재시도할 수 있다.

---

## 2. Phase 1 — 기반 배선

### 2-1. SDK 설치

```bash
cd frontend && npm install @tosspayments/tosspayments-sdk
```

### 2-2. 클라이언트 키 주입

클라이언트 키는 **브라우저 번들에 그대로 실리는 공개값**이다. 비밀은 백엔드의 `TOSS_SECRET_KEY` 쪽이다. 그래서 별도 API 없이 Vite 환경변수로 넣는다.

`frontend/.env.example` (커밋)
```
VITE_TOSS_CLIENT_KEY=
```

`frontend/.env` (gitignore 대상 — 루트 `.gitignore`의 `.env` 패턴이 하위 디렉터리에도 적용된다)
```
VITE_TOSS_CLIENT_KEY=test_ck_...
```

로컬 테스트 키는 리포지토리 루트 `env` 파일의 `TOSS_CLIENT_KEY`와 같은 값을 쓰면 된다.

> Vite는 `VITE_` 접두사가 붙은 값만 노출한다. `.env`를 새로 만들면 **dev 서버가 자동으로 재시작**되지만, 안 되면 수동 재시작한다. 반영 확인:
> ```bash
> curl -s http://localhost:5173/src/payments/toss.ts | grep -o "test_ck_[A-Za-z0-9]*"
> ```

---

## 3. Phase 2 — 결제창 모듈 (`src/payments/toss.ts`)

신규 파일. SDK 초기화와 결제창 호출만 담당한다.

```ts
import { ANONYMOUS, loadTossPayments, type TossPaymentsPayment } from "@tosspayments/tosspayments-sdk";

const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;

export function isTossConfigured(): boolean {
  return Boolean(clientKey);
}

let paymentPromise: Promise<TossPaymentsPayment> | null = null;

function getTossPayment(): Promise<TossPaymentsPayment> {
  if (!clientKey) return Promise.reject(new Error("VITE_TOSS_CLIENT_KEY가 없어요."));
  if (!paymentPromise) {
    paymentPromise = loadTossPayments(clientKey)
      .then((sdk) => sdk.payment({ customerKey: ANONYMOUS }))
      .catch((error: unknown) => {
        paymentPromise = null;   // 실패한 Promise를 캐시하면 이후 시도가 전부 막힌다
        throw error;
      });
  }
  return paymentPromise;
}
```

**포인트 세 가지**

1. `isTossConfigured()`를 노출해서 키가 없을 때 화면에서 결제 버튼 대신 안내를 띄운다. 키 누락을 런타임 에러가 아니라 UI로 드러낸다.
2. SDK 로드 Promise를 캐시하되 **실패 시 캐시를 비운다.** 안 그러면 일시적 실패가 영구 실패가 된다.
3. `customerKey`는 `ANONYMOUS`. 토스 문서가 "유추 가능한 값(이메일·전화번호·순번) 금지"라고 명시하므로 userId를 쓰지 않는다.

결제창 호출부:

```ts
export async function requestReservationPayment(request: {
  paymentId: number; reservationId: number;
  orderId: string; amount: number; orderName: string;
}): Promise<void> {
  const payment = await getTossPayment();
  const origin = window.location.origin;
  const query = `paymentId=${request.paymentId}&reservationId=${request.reservationId}`;

  await payment.requestPayment({
    method: "CARD",
    amount: { currency: "KRW", value: request.amount },
    orderId: request.orderId,
    orderName: request.orderName,
    successUrl: `${origin}/payments/success?${query}`,
    failUrl: `${origin}/payments/fail?${query}`,
    card: { flowMode: "DEFAULT", useEscrow: false, useCardPoint: false },
  });
}
```

**`successUrl`에 `paymentId`·`reservationId`를 우리가 직접 붙인다.** 토스는 리다이렉트할 때 `paymentKey`·`orderId`·`amount`만 덧붙여 주는데, 우리 confirm API는 `paymentId`로 대상을 식별하고 QR 조회에는 `reservationId`가 필요하기 때문이다.

- 정상 흐름에서 이 Promise는 **resolve되지 않는다** (리다이렉트로 페이지가 떠남)
- reject되는 경우 = 사용자가 결제창을 닫음 / SDK가 요청을 거절함

---

## 4. Phase 3 — API 계층 정리 (`src/api/payment.ts`)

기존 코드는 모든 결제 함수의 `userId`가 `TEMP_PAYER_USER_ID = 1` 기본값이었다. 결제 API는 아직 JWT가 아니라 `X-User-Id` 헤더로 결제자를 식별하는데, 백엔드가 **이 값과 예약 소유자를 비교**한다(`PaymentService.payReservationDeposit`).

→ 기본값을 두면 userId 1번 계정이 아닌 사용자는 **전부 `ACCESS_DENIED`로 막힌다.** 실제 사용자 흐름을 타는 두 함수만 `userId`를 필수 인자로 바꾼다.

```ts
// 기본값 제거 — 호출부가 로그인 사용자 ID를 반드시 넘기게 강제
export function createReservationDepositPayment(reservationId: number, userId: number) { ... }
export function confirmPayment(paymentId: number, paymentKey: string, userId: number) { ... }
```

`TEMP_PAYER_USER_ID` 상수는 **관리자 테스트 도구 전용**으로 남기고, `PaymentCreatePage`에서만 명시적으로 넘긴다. 그래야 임시값을 쓰는 지점이 코드에 드러난다.

> 로그인 사용자 ID는 `useAuth().user.userId`로 얻는다. JWT의 `sub`를 디코딩한 값이라 결제 API가 JWT로 넘어가기 전에도 정확하다.

---

## 5. Phase 4 — 예매 화면 (`TicketReservationPage.tsx`)

### 5-1. 유료 사전예약도 예약을 실제로 생성한다

기존 코드는 유료면 `createAdvanceReservation`을 **부르지 않고** 결제 단계로 넘어갔다. 결제 API가 `reservationId`로 원장 금액을 조회하므로 예약이 먼저 있어야 한다.

```ts
const created = await createAdvanceReservation(id, {
  visitDate: advanceVisitDate,
  ...(isPaid ? { reservationTermsAgreed: true, reservationTermsVersion: ADVANCE_TERMS_VERSION } : {}),
});

if (created.paymentRequired) {
  setPaymentInfo({
    reservationId: created.reservationId,
    amount: created.amount,                     // 화면의 price가 아니라 서버 계산값
    paymentExpiresAt: created.paymentExpiresAt,
    /* ... */
  });
  setPhase("payment");
  return;
}
// 무료: 서버가 한 트랜잭션에서 CONFIRMED + QR까지 끝낸다
```

유료 사전예약은 약관 동의가 필수다(`ReservationService.validateTerms`). 백엔드는 버전 문자열이 비어있지만 않으면 통과시키고 그대로 원장에 기록하므로, 프론트에 상수를 두고 동의 문구가 바뀌면 같이 올린다.

```ts
// src/api/reservation.ts
export const ADVANCE_TERMS_VERSION = "advance-paid-v1";
```

현장예매(`ONSITE`)는 이미 예약을 생성하고 있었으므로 `reservationId`·`paymentExpiresAt`만 결제 단계로 넘기면 된다.

### 5-2. 결제 제한시각 카운트다운

백엔드는 결제 제한시각을 **10분**으로 잡는다(`ReservationService.PAYMENT_WAIT_MINUTES`).

```ts
const [now, setNow] = useState(() => Date.now());

useEffect(() => {
  if (phase !== "payment") return;                    // 결제 단계에서만 타이머를 돈다
  const timer = window.setInterval(() => setNow(Date.now()), 1000);
  return () => window.clearInterval(timer);
}, [phase]);

const remaining = formatRemaining(paymentInfo.paymentExpiresAt, now);
const expired = paymentInfo.paymentExpiresAt !== null && remaining === null;
```

만료되면 결제 버튼을 잠근다. **현재 백엔드 `confirmPayment`는 만료를 검증하지 않으므로**, 만료 후 결제하면 결제는 승인되고 예약 통지만 거절당해 돈만 나간다. 프론트 가드가 지금은 유일한 방어선이다(백엔드 검증은 별도 과제).

### 5-3. 결제 실행

```ts
async function handlePay() {
  if (!paymentInfo || paying || expired) return;
  if (!user) { setPayError("로그인이 필요해요."); return; }

  setPaying(true);
  try {
    const created = await createReservationDepositPayment(paymentInfo.reservationId, user.userId);
    await requestReservationPayment({
      paymentId: created.paymentId,
      reservationId: paymentInfo.reservationId,
      orderId: created.orderId ?? `PAYMENT_${created.paymentId}`,
      amount: created.amount,
      orderName,
    });
    // 리다이렉트가 시작됐으므로 paying을 되돌리지 않는다
  } catch (err) {
    setPayError(/* ... */);
    setPaying(false);
  }
}
```

`orderName`은 토스 제한이 100자라 `.slice(0, 100)` 한다.

---

## 6. Phase 5 — 결제 결과 페이지 (`PaymentResultPage.tsx`)

라우트 등록 (`AppRouter.tsx`, `PublicLayout` 하위):

```tsx
<Route path="/payments/success" element={<PaymentSuccessPage />} />
<Route path="/payments/fail" element={<PaymentFailPage />} />
```

### 6-1. confirm 호출 전 3중 가드

```ts
// (1) 토큰 재발급(silent refresh)이 끝날 때까지 기다린다.
//     토스에서 돌아오면 앱이 새로 뜨면서 메모리의 accessToken이 사라지므로,
//     status가 확정되기 전에 confirm을 부르면 401로 실패한다.
if (status === "loading") return;

// (2) 파라미터 검증은 렌더 중 순수 계산으로 (이펙트에서 setState 금지)
let blockedReason: string | null = null;
if (paymentId === null || reservationId === null || !paymentKey) { ... }
else if (orderId !== null && orderId !== `PAYMENT_${paymentId}`) { ... }

// (3) 결제당 딱 한 번만 호출
const confirmStarted = useRef(false);
if (confirmStarted.current) return;
```

**(3)이 특히 중요하다.** 백엔드는 `PENDING`이 아닌 결제를 409로 막는다. 가드가 없으면 StrictMode의 이펙트 2회 실행과 사용자의 새로고침이 전부 중복 호출이 되어, **이미 성공한 결제가 실패 화면으로 보인다.**

**(2)** 는 lint 규칙(`react-hooks/set-state-in-effect`) 때문만이 아니다. 파라미터 검증은 렌더 입력만으로 결정되는 순수 계산이라 이펙트에 둘 이유가 없다.

`orderId` 대조는 백엔드가 `"PAYMENT_" + paymentId`로 orderId를 만들기 때문이다. 어긋나면 어차피 토스가 거절하므로 부르기 전에 걸러낸다.

### 6-2. QR은 따로 받아온다

confirm 응답(`PaymentResponse`)에는 QR 토큰이 없다. 승인이 확정되면 결제 도메인이 예약 도메인에 통지하고 그 시점에 QR이 발급되므로, 별도로 조회한다.

```ts
await confirmPayment(paymentId, paymentKey, userId);
try {
  const qr = await getEntryQr(reservationId);
  setEntryQrToken(qr.qrToken);
} catch {
  setEntryQrToken(null);   // 결제는 이미 성공 — QR 조회 실패를 결제 실패로 보여주지 않는다
}
```

### 6-3. 실패 페이지

예약은 `PENDING_PAYMENT`로 남아있다는 것과 재시도 경로만 안내한다. 별도 API 호출은 하지 않는다.

---

## 7. 검증

### 7-1. 정적 검사

```bash
cd frontend
npm run typecheck    # tsc -b
npm run build
npx eslint src/payments/toss.ts src/pages/payment/PaymentResultPage.tsx \
           src/pages/reservation/TicketReservationPage.tsx src/api/auth.ts
```

> 리포지토리 전체 `npm run lint`는 이 작업 **이전부터** 12개 파일에서 실패한다. 새로 만든 파일만 확인하면 된다.

### 7-2. 백엔드 체인 (브라우저 없이)

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/email/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"tester@petopia.local","password":"Test1234!"}' \
  | sed -E 's/.*"accessToken":"([^"]*)".*/\1/')

# 예약 생성 → PENDING_PAYMENT + paymentExpiresAt
curl -s -X POST "localhost:8080/api/v1/fairs/1/reservations" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"visitDate\":\"$(date -v+7d +%Y-%m-%d)\",\"reservationTermsAgreed\":true,\"reservationTermsVersion\":\"advance-paid-v1\"}"

# 결제 생성 → PENDING + orderId, 금액이 예약 원장에서 조회됨
curl -s -X POST "localhost:8080/api/reservations/{예약ID}/payment" -H "X-User-Id: {유저ID}"
```

여기까지 통과하면 예약↔결제 내부 계약 API가 붙어 있다는 뜻이다.

### 7-3. 화면

씨드 데이터는 `db/seed/local-payment-seed.sql` 참고 (계정 `tester@petopia.local` / `Test1234!`, 100원짜리 행사).

1. 로그인 → **F5 새로고침해도 로그인 유지되는지** (결제 복귀의 전제 조건, §8 참고)
2. `/tickets/1` → 사전예약 → 날짜 선택 → 약관 동의 → 다음
3. 결제 화면: 금액, 카운트다운 동작, 결제수단 안내 표시
4. 결제하기 → 토스 결제창
5. 승인 후 `/payments/success` → "결제가 완료됐어요" + QR
6. **DB 확인** — 화면만 믿지 않는다

```sql
SELECT payment_id, status, amount, toss_payment_key, paid_at FROM payment;
SELECT reservation_id, status, reserved_at FROM reservations;
SELECT * FROM reservation_payment_confirmations;
```

기대: 결제 `COMPLETED`, 예약 `CONFIRMED`, 통지 영수증 1건(`event_id = PAYMENT_{paymentId}`).
**결제만 `COMPLETED`이고 예약이 `PENDING_PAYMENT`면** 통지 실패다. 백엔드 로그에서 `예약 도메인 결제완료 통지`를 찾는다.

---

## 8. 함정 모음

| 증상 | 원인 |
|---|---|
| 결제 화면에 "결제 설정이 없어요" | `.env` 미반영 → dev 서버 재시작 |
| 결제창이 안 뜨고 SDK 에러 | 클라이언트 키가 **결제위젯용**. API 개별 연동 키로 교체 (§1-1) |
| 토스가 승인 거절(4xx) | `orderId`/`amount`를 가공했을 가능성. 서버 응답 그대로 넘겨야 한다 (§1-3) |
| 1원 결제가 안 됨 | 카드 결제 **최소 100원** |
| success 화면이 409로 실패 | confirm 중복 호출. `confirmStarted` 가드 확인 (§6-1) |
| 예약 생성이 R005 | 같은 행사에 활성 예약이 이미 있음 |

### 결제 밖의 전제 조건 — 인증 도메인

`/payments/success`는 **완전한 페이지 로드**로 진입하므로, 그 시점에 토큰 재발급(silent refresh)이 정상 동작해야 confirm을 부를 수 있다. 여기가 깨지면 화면에 "로그인이 풀렸어요"가 뜨고 **결제는 승인됐는데 예약이 확정되지 않는다.**

결제 코드에서 할 수 있는 건 `status === "loading"` 동안 기다리는 것까지다(§6-1). 재발급 자체가 실패한다면 **인증 도메인 이슈이므로 담당자에게 문의한다.** 증상 구분:

| 새로고침 후 | 판정 |
|---|---|
| 로그인 유지됨 | 정상. 결제 흐름 진행 가능 |
| 로그아웃됨 + `/api/auth/refresh`가 401(A011) | 인증 도메인 이슈 |
| 로그아웃됨 + `/api/auth/refresh`가 400(C001) | 쿠키가 요청에 없음. 로그인 전이면 정상 동작이다 |

---

## 9. 남은 과제 (이번 범위 밖)

이번 작업은 **정상 경로**를 완성한 것이고, 아래는 붙지 않았다.

1. **결제 API의 JWT 전환** — 지금은 `/api/payments/**`가 `permitAll` + `X-User-Id` 헤더다. 누구나 남의 ID로 결제·승인을 시도할 수 있다.
2. **`/internal/api/v1/**` 외부 차단** — 헤더 문자열만 흉내내면 결제 없이 예약을 `CONFIRMED`로 만들고 입장 QR까지 받을 수 있다. **우선순위 최상.**
3. **백엔드 결제 만료 검증** — `confirmPayment`가 `paymentExpiresAt`을 보지 않는다. 현재는 프론트 가드가 유일한 방어선이다(§5-2).
4. **예약 취소·만료 → 결제 취소·만료 연결** — 계약 API(`/internal/api/v1/payments/{id}/cancel|expire`)는 이미 있는데 **호출부가 없다.** 결제창 이탈 시마다 `PENDING` 결제가 유령으로 쌓인다.
5. **통지 실패 보정 배치** — 결제 `COMPLETED` + 예약 `PENDING_PAYMENT`로 갈라진 건을 자동 복구하는 수단이 없다.
6. **토스 웹훅 수신** — confirm 요청이 유실되면 복구 경로가 전무하다.
