package com.ms.petopia.api.payment.dto;

/** 예약 도메인의 결제완료 통지(POST .../reservation-payment-completions) 응답을 그대로 매핑. */
public record ReservationPaymentCompletionResult(Long reservationId,
                                                 String reservationStatus,
                                                 boolean idempotentReplay,
                                                 String entryQrToken) {
}
