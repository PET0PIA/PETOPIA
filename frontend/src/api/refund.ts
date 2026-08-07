import { apiClient } from "./client";

export type RefundReason = "USER_CANCEL" | "FAIR_CANCEL_USER" | "VENDOR_CANCEL" | "FAIR_CANCEL_VENDOR" | "OPENING_FEE_MANUAL";
export type RequestedByDomain = "RESERVATION" | "FAIR" | "VENDOR" | "PAYMENT_ADMIN";

export interface RefundDetail {
  refundId: number;
  paymentId: number;
  // 환불 대상 결제가 예약금 결제일 때만 값이 있음(참가비/개설비 환불이면 null).
  reservationId: number | null;
  refundReason: RefundReason;
  requestedByDomain: RequestedByDomain;
  refundAmount: number;
  status: string;
  requestedAt: string;
  processedAt: string | null;
}

/**
 * 결제 한 건에 걸린 환불 내역 조회. UK_REFUND_PAYMENT 제약상 결제당 환불은 최대 1건이라
 * 배열이지만 항상 0개 또는 1개다.
 */
export function getRefundsByPayment(paymentId: number) {
  return apiClient.get<RefundDetail[]>(`/api/payments/${paymentId}/refunds`);
}

/** 환불 단건 상세 조회. */
export function getRefund(refundId: number) {
  return apiClient.get<RefundDetail>(`/api/refunds/${refundId}`);
}
