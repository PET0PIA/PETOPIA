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

export interface ReservationPaymentRequest {
  paymentId: number;
  reservationId: number;
  /** 결제 생성 응답의 orderId를 가공 없이 그대로 넘긴다("PAYMENT_{paymentId}"). */
  orderId: string;
  /** 화면에 보여준 예약금이 아니라 서버가 계산한 금액. */
  amount: number;
  orderName: string;
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
  const payment = await getTossPayment();
  const origin = window.location.origin;

  // 토스가 리다이렉트에 붙여주는 건 paymentKey·orderId·amount뿐이다.
  // confirm API는 paymentId로 대상을 식별하고 QR 조회에는 reservationId가 필요해서 직접 붙인다.
  const query = `paymentId=${request.paymentId}&reservationId=${request.reservationId}`;

  await payment.requestPayment({
    method: "CARD",
    amount: { currency: "KRW", value: request.amount },
    orderId: request.orderId,
    orderName: request.orderName.slice(0, ORDER_NAME_MAX_LENGTH),
    successUrl: `${origin}/payments/success?${query}`,
    failUrl: `${origin}/payments/fail?${query}`,
    card: { flowMode: "DEFAULT", useEscrow: false, useCardPoint: false },
  });
}

export interface VendorFeePaymentRequest {
  paymentId: number;
  applicationId: number;
  /** 결제 생성 응답의 orderId를 가공 없이 그대로 넘긴다("PAYMENT_{paymentId}"). */
  orderId: string;
  /** 화면에 보여준 금액이 아니라 서버가 확정한 금액(승인 시 finalPrice). */
  amount: number;
  orderName: string;
}

/**
 * 토스 결제창을 띄운다(참가비). requestReservationPayment와 동일한 결제창 방식 —
 * successUrl 리다이렉트라 정상 흐름에서 이 Promise는 resolve되지 않는다.
 */
export async function requestVendorFeePayment(request: VendorFeePaymentRequest): Promise<void> {
  const payment = await getTossPayment();
  const origin = window.location.origin;

  // confirm API는 paymentId로 대상을 식별하고, 성공 페이지는 결제유형 분기를 위해
  // applicationId를 쓴다(예약금 쪽 reservationId와 같은 역할).
  const query = `paymentId=${request.paymentId}&applicationId=${request.applicationId}`;

  await payment.requestPayment({
    method: "CARD",
    amount: { currency: "KRW", value: request.amount },
    orderId: request.orderId,
    orderName: request.orderName.slice(0, ORDER_NAME_MAX_LENGTH),
    successUrl: `${origin}/payments/success?${query}`,
    failUrl: `${origin}/payments/fail?${query}`,
    card: { flowMode: "DEFAULT", useEscrow: false, useCardPoint: false },
  });
}




export interface FairOpeningFeePaymentRequest {
  paymentId: number;
  fairId: number;
  /** 결제 생성 응답의 orderId를 가공 없이 그대로 넘긴다("PAYMENT_{paymentId}"). */
  orderId: string;
  /** 화면에 보여준 개설비가 아니라 서버가 승인 시 확정해 저장해둔 금액. */
  amount: number;
  orderName: string;
}

/**
 * 개설비 결제창을 띄운다. successUrl·failUrl은 예약금 결제와 같은 착지 경로
 * (/payments/success, /payments/fail)를 쓰되 reservationId 대신 fairId를 싣는다 -
 * 그 착지 페이지가 fairId 유무로 예약금/개설비 흐름을 구분해 confirm까지 처리한다.
 */
export async function requestFairOpeningFeePayment(request: FairOpeningFeePaymentRequest): Promise<void> {
  const payment = await getTossPayment();
  const origin = window.location.origin;

  const query = `paymentId=${request.paymentId}&fairId=${request.fairId}`;

  await payment.requestPayment({
    method: "CARD",
    amount: { currency: "KRW", value: request.amount },
    orderId: request.orderId,
    orderName: request.orderName.slice(0, ORDER_NAME_MAX_LENGTH),
    successUrl: `${origin}/payments/success?${query}`,
    failUrl: `${origin}/payments/fail?${query}`,
    card: { flowMode: "DEFAULT", useEscrow: false, useCardPoint: false },
  });
}
