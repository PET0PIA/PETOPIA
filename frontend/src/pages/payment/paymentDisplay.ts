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
  RESERVATION_DEPOSIT: "예약 예약금",
  VENDOR_FEE: "참가업체 참가비",
  FAIR_OPENING_FEE: "행사 개설비",
};

export function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

export function formatAmount(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}
