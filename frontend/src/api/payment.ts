import { apiClient } from "./client";

/**
 * 회원·인증 도메인 연동 전까지 쓰는 임시 사용자 ID다.
 * 백엔드 PaymentTemporaryAuthHeaders와 대응된다.
 * TODO 인증 도메인 완성 후 로그인 사용자 정보로 교체한다.
 */
export const TEMP_USER_ID_HEADER = "X-User-Id";
export const TEMP_PAYER_USER_ID = 1;

export type PaymentType = "RESERVATION_DEPOSIT" | "VENDOR_FEE" | "FAIR_OPENING_FEE";
export type PaymentStatus = "PENDING" | "COMPLETED" | "FAILED" | "CANCELED" | "EXPIRED";

export interface PaymentDetail {
  paymentId: number;
  // 참가비 결제(토스 실연동)만 채워짐. "PAYMENT_{paymentId}" 형식, 토스 결제위젯 orderId로 쓰인다.
  orderId: string | null;
  paymentType: PaymentType;
  amount: number;
  status: PaymentStatus;
  method: string;
  paidAt: string | null;
  createdAt: string;
  fairId: number;
  businessId: number | null;
  payerUserId: number | null;
  reservationId: number | null;
  applicationId: number | null;
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

/** 예약금 결제 생성. 예약이 "정원 임시선점" 상태여야 하고, 즉시 COMPLETED로 처리된다(모의결제). */
export function createReservationDepositPayment(reservationId: number, userId: number = TEMP_PAYER_USER_ID) {
  return apiClient.post<PaymentDetail>(`/api/reservations/${reservationId}/payment`, undefined, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/**
 * 참가비 결제 준비(토스 실연동). 신청 상태가 APPROVED여야 하고, 응답은 PENDING + orderId로 온다.
 * 실제 결제 완료는 별도로 confirmPayment 호출까지 이어져야 한다.
 */
export function createVendorFeePayment(
  applicationId: number,
  payload: { fairId: number; businessId: number; amount: number },
  userId: number = TEMP_PAYER_USER_ID,
) {
  return apiClient.post<PaymentDetail>(`/api/vendor-applications/${applicationId}/payment`, payload, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/** 행사개설비 결제 생성. fair 상태 검증 없이 요청 금액을 그대로 신뢰한다(모의결제, 즉시 COMPLETED). */
export function createFairOpeningPayment(fairId: number, amount: number, userId: number = TEMP_PAYER_USER_ID) {
  return apiClient.post<PaymentDetail>(`/api/fairs/${fairId}/opening-payment`, { amount }, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/**
 * 결제 승인 확정(토스 confirm). PENDING으로 생성된 결제를 토스 결제창 완료 후 받은 paymentKey로
 * 확정시킨다. orderId·amount는 서버가 자체적으로 판단하므로 paymentKey만 보낸다.
 */
export function confirmPayment(paymentId: number, paymentKey: string, userId: number = TEMP_PAYER_USER_ID) {
  return apiClient.post<PaymentDetail>(`/api/payments/${paymentId}/confirm`, { paymentKey }, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

export interface PaymentListFilters {
  fairId?: number;
  businessId?: number;
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
