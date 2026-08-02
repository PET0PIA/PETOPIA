package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

/** 결제 도메인이 클라이언트 금액을 신뢰하지 않고 예약 원장을 조회하기 위한 계약. */
public record ReservationPaymentContextResponse(
        Long reservationId,
        Long fairId,
        Long payerUserId,
        String reservationType,
        long amount,
        LocalDateTime paymentExpiresAt
) {
}
