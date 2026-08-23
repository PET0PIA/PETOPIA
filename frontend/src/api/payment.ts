import { apiClient } from "./client";

/**
 * 결제 API는 아직 JWT가 아니라 X-User-Id 헤더로 결제자를 식별한다.
 * 백엔드 PaymentTemporaryAuthHeaders와 대응된다.
 * TODO 인증 도메인 완성 후 JWT로 교체한다.
 */
export const TEMP_USER_ID_HEADER = "X-User-Id";

/**
 * 인증 도메인 연동 전까지 쓰는 임시 결제자 ID 기본값.
 *
 * 실제 사용자 흐름을 타는 함수(createReservationDepositPayment·confirmPayment)에는
 * 이 값을 기본값으로 두지 않는다 — 백엔드가 X-User-Id와 예약 소유자를 대조하기 때문에
 * 기본값을 두면 userId 1번이 아닌 사용자가 전부 ACCESS_DENIED로 막힌다.
 * 임시값을 쓰는 지점이 코드에 드러나도록 호출부에서 명시적으로 넘긴다.
 */
export const TEMP_PAYER_USER_ID = 1;

export type PaymentType = "RESERVATION_DEPOSIT" | "VENDOR_FEE" | "FAIR_OPENING_FEE";
// WAITING_FOR_DEPOSIT: 가상계좌 발급 완료, 아직 입금 전(토스 입금 웹훅이 오면 COMPLETED로 전환됨).
export type PaymentStatus = "PENDING" | "WAITING_FOR_DEPOSIT" | "COMPLETED" | "FAILED" | "CANCELED" | "EXPIRED";

export interface PaymentDetail {
  paymentId: number;
  // 토스 결제위젯에 넘길 주문번호. 결제 시도마다 서버가 새로 발급해 저장하므로
  // 화면에서 유추하거나 가공하지 말고 받은 값을 그대로 넘긴다.
  orderId: string;
  paymentType: PaymentType;
  amount: number;
  status: PaymentStatus;
  method: string;
  paidAt: string | null;
  createdAt: string;
  fairId: number;
  // 아래 fairName·fairManager*·businessName·applicationManager*는 fairs/business/
  // application_form을 LEFT JOIN해서 채운다 - refund* 7개와 동일한 이유로 getPayments/
  // getMyPayments(목록)와 getPayment(상세)만 채워준다. 그 외 결제 생성/확정 응답(createXxxPayment/
  // confirmPayment)에는 이 JOIN이 없어서 전부 null로 온다 - CodeRabbit 지적(PR #258),
  // 실제로는 null일 수 있는데 fairName/fairManagerName/fairManagerEmail을 non-null로
  // 잘못 선언해뒀었다.
  fairName: string | null;
  // 행사 등록 신청 시 입력한 담당자 정보. manager_phone만 원래 선택 입력이라 null일 수 있다.
  fairManagerName: string | null;
  fairManagerPhone: string | null;
  fairManagerEmail: string | null;
  businessId: number | null;
  businessName: string | null;
  // 부스(참가) 신청서 작성 시 입력한 담당자 정보. VENDOR_FEE 결제가 아니면 전부 null.
  applicationManagerName: string | null;
  applicationManagerPhone: string | null;
  applicationManagerEmail: string | null;
  payerUserId: number | null;
  reservationId: number | null;
  applicationId: number | null;
  // 간편결제(네이버페이 등)로 결제했을 때만 값이 있다. 일반 카드/계좌이체 등이면 null.
  easyPayProvider: string | null;
  // 아래 3개는 가상계좌로 결제했을 때만 값이 있다(status가 WAITING_FOR_DEPOSIT/COMPLETED일 때).
  virtualAccountBankCode: string | null;
  virtualAccountNumber: string | null;
  virtualAccountDueDate: string | null;
  // 아래 7개는 이 결제에 걸린 환불이 있을 때만 값이 있다(없으면 전부 null). getPayments/
  // getMyPayments(목록)와 getPayment(상세)만 채워준다 - 그 외 결제 생성/확정 응답에는 항상 null.
  refundId: number | null;
  refundStatus: "REQUESTED" | "COMPLETED" | "REJECTED" | null;
  refundAmount: number | null;
  refundReason: string | null;
  refundRequestedByDomain: string | null;
  refundRequestedAt: string | null;
  refundProcessedAt: string | null;
}

export interface PaymentListResult {
  content: PaymentDetail[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function getPayment(paymentId: number, userId: number = TEMP_PAYER_USER_ID) {
  return apiClient.get<PaymentDetail>(`/api/payments/${paymentId}`, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/**
 * 예약금 결제 준비(토스 실연동). 예약이 PENDING_PAYMENT 상태여야 하고, 응답은 PENDING + orderId로 온다.
 * 금액은 보내지 않는다 — 백엔드가 예약 도메인의 결제 컨텍스트에서 원장 금액을 조회한다.
 * 실제 결제 완료는 토스 결제창 → confirmPayment까지 이어져야 한다.
 *
 * userId는 기본값 없이 필수다. 백엔드가 이 값을 예약 소유자와 대조하므로(ACCESS_DENIED)
 * 호출부가 로그인 사용자 ID를 반드시 넘겨야 한다.
 */
export function createReservationDepositPayment(reservationId: number, userId: number) {
  return apiClient.post<PaymentDetail>(`/api/reservations/${reservationId}/payment`, undefined, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/**
 * 참가비 결제 준비(토스 실연동). 신청 상태가 결제 대기(PAYMENT_PENDING)여야 하고, 응답은
 * PENDING + orderId로 온다. 예약금·개설비와 동일하게 금액을 보내지 않는다 — 백엔드가
 * 참가업체 도메인의 결제 컨텍스트에서 승인 시 확정된 금액을 조회한다(2026-08-20 해소,
 * 예전엔 클라이언트가 fairId·businessId·amount를 직접 실어보냈음).
 * 실제 결제 완료는 별도로 confirmPayment 호출까지 이어져야 한다.
 *
 * userId는 기본값 없이 필수다. 백엔드가 이 값을 사업자 소유주와 대조하므로(ACCESS_DENIED)
 * 호출부가 로그인 사용자 ID를 반드시 넘겨야 한다.
 */
export function createVendorFeePayment(applicationId: number, userId: number) {
  return apiClient.post<PaymentDetail>(`/api/vendor-applications/${applicationId}/payment`, undefined, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/**
 * 행사개설비 결제 생성. 예약금과 동일하게 금액을 보내지 않는다 - 서버가 승인 시 확정된
 * fairs.opening_fee_amount를 그대로 써서 결제를 만든다(클라이언트가 보낸 금액을 더 이상
 * 신뢰하지 않음). 실제 결제 완료는 별도로 confirmPayment 호출까지 이어져야 한다.
 */
export function createFairOpeningPayment(fairId: number, userId: number = TEMP_PAYER_USER_ID) {
  return apiClient.post<PaymentDetail>(`/api/fairs/${fairId}/opening-payment`, undefined, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/**
 * 결제 승인 확정(토스 confirm). PENDING으로 생성된 결제를 토스 결제창 완료 후 받은 paymentKey로
 * 확정시킨다. orderId·amount는 서버가 자체적으로 판단하므로 paymentKey만 보낸다.
 *
 * userId는 기본값 없이 필수다 — 백엔드가 결제의 payerUserId와 대조한다(ACCESS_DENIED).
 * PENDING이 아닌 결제를 다시 확정하려 하면 409로 막히므로, 호출부는 중복 호출을 스스로 막아야 한다.
 */
export function confirmPayment(paymentId: number, paymentKey: string, userId: number) {
  return apiClient.post<PaymentDetail>(`/api/payments/${paymentId}/confirm`, { paymentKey }, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

export interface PaymentListFilters {
  fairId?: number;
  businessId?: number;
  reservationId?: number;
  paymentType?: PaymentType;
  status?: PaymentStatus;
  page?: number;
  size?: number;
}

/** 조건별 결제 목록(관리자용). 필터는 전부 선택적이며 AND로 조합된다. */
export function getPayments(filters: PaymentListFilters = {}) {
  const params = new URLSearchParams();
  if (filters.fairId !== undefined) params.set("fairId", String(filters.fairId));
  if (filters.businessId !== undefined) params.set("businessId", String(filters.businessId));
  if (filters.reservationId !== undefined) params.set("reservationId", String(filters.reservationId));
  if (filters.paymentType) params.set("paymentType", filters.paymentType);
  if (filters.status) params.set("status", filters.status);
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 20));
  return apiClient.get<PaymentListResult>(`/api/payments?${params.toString()}`);
}

/** 로그인 사용자 본인의 결제 내역(마이페이지). */
export function getMyPayments(userId: number = TEMP_PAYER_USER_ID, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiClient.get<PaymentListResult>(`/api/me/payments?${params.toString()}`, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}
