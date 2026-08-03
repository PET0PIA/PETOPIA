package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

/** 결제 도메인이 검증을 마친 성공 결과를 예약 도메인에 전달할 때 사용하는 계약. */
public record ReservationPaymentCompletedCommand(
        String eventId,
        Long paymentId,
        Long reservationId,
        Long paidAmount,
        LocalDateTime paidAt
) {
}
