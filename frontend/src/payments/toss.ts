/**
 * 토스페이먼츠 결제창 연동.
 *
 * 결제위젯(widgets)이 아니라 결제창(payment)을 쓴다. 위젯은 토스 어드민에 위젯 설정을
 * 등록해야 초기화가 되는데, 실패했을 때 그게 우리 코드 문제인지 어드민 설정 문제인지
 * 구분하기 어려워 연동 초기에는 변수를 줄이는 쪽을 골랐다.
 *
 * 두 방식은 클라이언트 키 종류가 다르다 — 결제창은 "API 개별 연동 키",
 * 위젯은 "결제위젯 연동 키". 위젯으로 바꾸려면 키도 같이 바꿔야 한다.
 */
import { ANONYMOUS, loadTossPayments, type TossPaymentsPayment } from "@tosspayments/tosspayments-sdk";

/**
 * 클라이언트 키는 브라우저 번들에 그대로 실리는 공개값이다. 비밀은 백엔드의
 * TOSS_SECRET_KEY 쪽이라 별도 API 없이 Vite 환경변수로 넣는다.
 */
const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;

/** 토스 결제 주문명 제한(100자). 넘기면 토스가 요청을 거절한다. */
const ORDER_NAME_MAX_LENGTH = 100;

/**
 * 키가 주입됐는지. 화면이 이 값을 보고 결제 버튼 대신 안내를 띄운다 —
 * 키 누락을 런타임 에러가 아니라 UI로 드러내려는 목적이다.
 */
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
        // 실패한 Promise를 캐시해두면 일시적 실패가 영구 실패가 된다.
        paymentPromise = null;
        throw error;
      });
  }
  return paymentPromise;
}

/**
 * 결제수단 선택지. "CARD"가 기본값(일반 카드/간편결제 통합결제창)이다.
 *
 * 네이버페이는 토스 SDK에서 별도 method가 아니라 - method는 여전히 "CARD"이고,
 * `card.flowMode: "DIRECT"` + `card.easyPay` 코드로 그 간편결제사 자체창을 바로 여는
 * 방식이다(카드/간편결제를 CARD 하나로 묶어서 취급함). 가상계좌만 진짜 다른 method다.
 */
export type PaymentMethodOption = "CARD" | "NAVER_PAY" | "VIRTUAL_ACCOUNT";

/**
 * 예약금 결제(사전예약·현장예매)가 쓸 수 있는 결제수단 — 가상계좌를 뺀 나머지.
 *
 * 예약은 결제 제한시간이 10분인데 가상계좌는 입금까지 하루 단위라 애초에 성립하지 않는다.
 * 늦게 입금되면 "결제는 됐는데 예약은 만료" 상태가 되어 돈만 받은 셈이 된다(이슈 #167).
 * 화면에서 선택지를 감추는 것만으로는 옛 번들·직접 호출을 못 막으므로, 예약금 결제 함수의
 * method 타입 자체를 좁혀 컴파일 단계에서 막는다.
 *
 * 참가비·개설비(사업자)는 결제 기한이 일 단위라 가상계좌를 계속 쓴다 — 그쪽은
 * {@link PaymentMethodOption}을 그대로 쓴다.
 */
export type ReservationPaymentMethod = Exclude<PaymentMethodOption, "VIRTUAL_ACCOUNT">;

/**
 * 참가비·개설비(사업자) 결제가 쓰는 목록 — 전체 3종.
 * 이쪽은 결제 기한이 일(day) 단위라 입금까지 시간이 걸리는 가상계좌와 궁합이 맞다.
 */
export const ALL_PAYMENT_METHODS: readonly PaymentMethodOption[] = ["CARD", "NAVER_PAY", "VIRTUAL_ACCOUNT"];

/**
 * 예약금 결제(사전예약·현장예매)가 쓰는 목록 — 가상계좌를 뺐다.
 * 이유는 {@link ReservationPaymentMethod} 주석 참고.
 */
export const RESERVATION_PAYMENT_METHODS: readonly ReservationPaymentMethod[] = ["CARD", "NAVER_PAY"];

interface CheckoutParams {
  method: PaymentMethodOption;
  amount: number;
  orderId: string;
  orderName: string;
  successUrl: string;
  failUrl: string;
}

/**
 * 결제수단 선택지별로 실제 requestPayment 호출까지 담당한다. requestPayment는 method
 * 리터럴값에 따라 오버로드가 갈리는 함수라, 유니언 타입 하나로 뭉쳐서 한 번에 스프레드해
 * 넘기면 TS가 오버로드를 못 고른다(예전에 시도했다가 컴파일 에러) — 그래서 분기마다
 * method를 리터럴로 직접 박아 별도로 호출한다.
 *
 * 가상계좌는 기한(validHours)·현금영수증 옵션이 필수급이라 여기서 기본값을 같이 정한다 —
 * 우선 24시간 고정. 이 분기를 타는 건 참가비·개설비 결제뿐이다(예약금은 가상계좌 미지원,
 * {@link ReservationPaymentMethod} 참고).
 */
async function openTossCheckout(params: CheckoutParams): Promise<void> {
  const payment = await getTossPayment();
  const common = {
    amount: { currency: "KRW" as const, value: params.amount },
    orderId: params.orderId,
    orderName: params.orderName.slice(0, ORDER_NAME_MAX_LENGTH),
    successUrl: params.successUrl,
    failUrl: params.failUrl,
  };

  switch (params.method) {
    case "NAVER_PAY":
      await payment.requestPayment({
        ...common,
        method: "CARD",
        card: { flowMode: "DIRECT", easyPay: "NAVERPAY", useEscrow: false, useCardPoint: false },
      });
      return;
    case "VIRTUAL_ACCOUNT":
      await payment.requestPayment({
        ...common,
        method: "VIRTUAL_ACCOUNT",
        virtualAccount: { cashReceipt: { type: "미발행" }, useEscrow: false, validHours: 24 },
      });
      return;
    case "CARD":
    default:
      await payment.requestPayment({
        ...common,
        method: "CARD",
        card: { flowMode: "DEFAULT", useEscrow: false, useCardPoint: false },
      });
      return;
  }
}

export interface ReservationPaymentRequest {
  paymentId: number;
  reservationId: number;
  /**
   * 착지 페이지가 대기 슬롯을 반납할 때 쓴다. 결제 실패 착지에는 결제 정보를 조회할
   * 근거가 없어서(예약을 되짚어야 한다) 결제창을 띄우는 쪽에서 실어 보낸다.
   */
  fairId?: number;
  /** 결제 생성 응답의 orderId를 가공 없이 그대로 넘긴다. 서버가 시도마다 발급한 값이다. */
  orderId: string;
  /** 화면에 보여준 예약금이 아니라 서버가 계산한 금액. */
  amount: number;
  orderName: string;
  /**
   * 생략하면 "CARD"(일반 카드/간편결제 통합결제창).
   * 가상계좌는 예약금 결제에서 지원하지 않는다({@link ReservationPaymentMethod} 참고).
   */
  method?: ReservationPaymentMethod;
}

/**
 * 토스 결제창을 띄운다. successUrl을 주는 리다이렉트 방식이라, 정상 흐름에서 이 Promise는
 * resolve되지 않는다(리다이렉트로 페이지가 떠남). reject되는 경우는 사용자가 결제창을
 * 닫았거나 SDK가 요청 자체를 거절한 경우다.
 *
 * customerKey는 ANONYMOUS로 고정한다 — 토스 문서가 "유추 가능한 값(이메일·전화번호·순번)
 * 금지"라고 명시하므로 userId를 쓰지 않는다.
 */
export async function requestReservationPayment(request: ReservationPaymentRequest): Promise<void> {
  const origin = window.location.origin;

  // 토스가 리다이렉트에 붙여주는 건 paymentKey·orderId·amount뿐이다.
  // confirm API는 paymentId로 대상을 식별하고 QR 조회에는 reservationId가 필요해서 직접 붙인다.
  // fairId는 착지 페이지의 결제유형 분기(개설비 결제)와 겹치지 않는다 - 그쪽은
  // reservationId가 없는 경우로 판별하므로, 예약금 결제에 fairId가 함께 실려도 무방하다.
  const query =
    `paymentId=${request.paymentId}&reservationId=${request.reservationId}` +
    (request.fairId != null ? `&fairId=${request.fairId}` : "");

  await openTossCheckout({
    method: request.method ?? "CARD",
    amount: request.amount,
    orderId: request.orderId,
    orderName: request.orderName,
    successUrl: `${origin}/payments/success?${query}`,
    failUrl: `${origin}/payments/fail?${query}`,
  });
}

export interface VendorFeePaymentRequest {
  paymentId: number;
  applicationId: number;
  /** 결제 생성 응답의 orderId를 가공 없이 그대로 넘긴다. 서버가 시도마다 발급한 값이다. */
  orderId: string;
  /** 화면에 보여준 금액이 아니라 서버가 확정한 금액(승인 시 finalPrice). */
  amount: number;
  orderName: string;
  /** 생략하면 "CARD"(일반 카드/간편결제 통합결제창). */
  method?: PaymentMethodOption;
}

/**
 * 토스 결제창을 띄운다(참가비). requestReservationPayment와 동일한 결제창 방식 —
 * successUrl 리다이렉트라 정상 흐름에서 이 Promise는 resolve되지 않는다.
 */
export async function requestVendorFeePayment(request: VendorFeePaymentRequest): Promise<void> {
  const origin = window.location.origin;

  // confirm API는 paymentId로 대상을 식별하고, 성공 페이지는 결제유형 분기를 위해
  // applicationId를 쓴다(예약금 쪽 reservationId와 같은 역할).
  const query = `paymentId=${request.paymentId}&applicationId=${request.applicationId}`;

  await openTossCheckout({
    method: request.method ?? "CARD",
    amount: request.amount,
    orderId: request.orderId,
    orderName: request.orderName,
    successUrl: `${origin}/payments/success?${query}`,
    failUrl: `${origin}/payments/fail?${query}`,
  });
}




export interface FairOpeningFeePaymentRequest {
  paymentId: number;
  fairId: number;
  /** 결제 생성 응답의 orderId를 가공 없이 그대로 넘긴다. 서버가 시도마다 발급한 값이다. */
  orderId: string;
  /** 화면에 보여준 개설비가 아니라 서버가 승인 시 확정해 저장해둔 금액. */
  amount: number;
  orderName: string;
  /** 생략하면 "CARD"(일반 카드/간편결제 통합결제창). */
  method?: PaymentMethodOption;
}

/**
 * 개설비 결제창을 띄운다. successUrl·failUrl은 예약금 결제와 같은 착지 경로
 * (/payments/success, /payments/fail)를 쓰되 reservationId 대신 fairId를 싣는다 -
 * 그 착지 페이지가 fairId 유무로 예약금/개설비 흐름을 구분해 confirm까지 처리한다.
 */
export async function requestFairOpeningFeePayment(request: FairOpeningFeePaymentRequest): Promise<void> {
  const origin = window.location.origin;

  const query = `paymentId=${request.paymentId}&fairId=${request.fairId}`;

  await openTossCheckout({
    method: request.method ?? "CARD",
    amount: request.amount,
    orderId: request.orderId,
    orderName: request.orderName,
    successUrl: `${origin}/payments/success?${query}`,
    failUrl: `${origin}/payments/fail?${query}`,
  });
}
