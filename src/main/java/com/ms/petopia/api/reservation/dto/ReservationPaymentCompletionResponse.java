package com.ms.petopia.api.reservation.dto;

public record ReservationPaymentCompletionResponse(
        Long reservationId,
        String reservationStatus,
        boolean idempotentReplay,
        String entryQrToken
) {
}
