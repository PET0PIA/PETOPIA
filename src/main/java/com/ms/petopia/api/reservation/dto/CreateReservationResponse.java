package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

/**
 * 예약 생성 결과.
 *
 * @param reservationId     예약 PK
 * @param reservationNo     사용자에게 보여줄 예약번호
 * @param reservationType   ADVANCE
 * @param reservationStatus 무료 예약은 CONFIRMED, 유료 예약은 PENDING_PAYMENT
 * @param amount            서버에서 확정한 예약금
 * @param paymentRequired   후속 결제가 필요한지 여부
 * @param paymentExpiresAt  유료 예약의 결제 제한시각
 * @param entryQrToken      무료 예약에서 즉시 발급된 입장 QR 토큰
 */
public record CreateReservationResponse(
        Long reservationId,
        String reservationNo,
        String reservationType,
        String reservationStatus,
        long amount,
        boolean paymentRequired,
        LocalDateTime paymentExpiresAt,
        String entryQrToken
) {
}
