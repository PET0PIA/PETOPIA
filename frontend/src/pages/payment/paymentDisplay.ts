import type { PaymentStatus, PaymentType } from "../../api/payment";

// 결제 상세/생성/목록 페이지가 공통으로 쓰는 표시용 매핑·포맷 함수.
export const statusLabels: Record<PaymentStatus, string> = {
  PENDING: "결제 대기",
  WAITING_FOR_DEPOSIT: "입금 대기",
  COMPLETED: "결제 완료",
  FAILED: "결제 실패",
  CANCELED: "결제 취소",
  EXPIRED: "만료됨",
};

export const statusTone: Record<PaymentStatus, "leaf" | "sun" | "neutral" | "primary"> = {
  PENDING: "sun",
  WAITING_FOR_DEPOSIT: "sun",
  COMPLETED: "leaf",
  FAILED: "primary",
  CANCELED: "neutral",
  EXPIRED: "neutral",
};

export const paymentTypeLabels: Record<PaymentType, string> = {
  RESERVATION_DEPOSIT: "관람객 예약금",
  VENDOR_FEE: "참가업체 참가비",
  FAIR_OPENING_FEE: "행사 개설비",
};

/**
 * 결제 상태 배지에 쓸 라벨 - 결제 자체 상태(statusLabels)와 달리, 완료된 환불이 있으면
 * "환불완료"로 덮어써서 보여준다. payment.status는 환불 이후에도 COMPLETED로 그대로 남기
 * 때문에(REFUND 테이블이 별도 관리) 목록/상세 어디서든 이 함수로 판단해야 한다.
 */
export function paymentStatusLabel(status: PaymentStatus, refundStatus: string | null): string {
  if (refundStatus === "COMPLETED") return "환불완료";
  return statusLabels[status] ?? status;
}

export function paymentStatusTone(status: PaymentStatus, refundStatus: string | null): "leaf" | "sun" | "neutral" | "primary" {
  if (refundStatus === "COMPLETED") return "neutral";
  return statusTone[status] ?? "neutral";
}

export const refundReasonLabels: Record<string, string> = {
  USER_CANCEL: "관람객 자진 예약취소",
  FAIR_CANCEL_USER: "행사취소로 인한 관람객예약 일괄취소",
  VENDOR_CANCEL: "참가업체 자진취소",
  FAIR_CANCEL_VENDOR: "행사취소로 인한 참가업체 환불",
  FAIR_CANCEL_OPENING_FEE: "행사취소로 인한 개설비 환불",
  OPENING_FEE_MANUAL: "행사 개설비 환불(관리자 수동)",
  ADMIN_CANCEL: "관리자 대행 예약취소",
};

export const refundRequestedByDomainLabels: Record<string, string> = {
  RESERVATION: "예약 도메인",
  FAIR: "행사 도메인",
  VENDOR: "참가업체 도메인",
  PAYMENT_ADMIN: "결제 관리자",
};

export const refundStatusLabels: Record<string, string> = {
  REQUESTED: "요청됨",
  COMPLETED: "완료",
  REJECTED: "거절됨",
};

export function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

export function formatAmount(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}
