package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateOnsiteReservationResponse(
        Long reservationId,
        String reservationNo,
        String reservationType,
        LocalDate visitDate,
        String reservationStatus,
        long amount,
        boolean paymentRequired,
        LocalDateTime paymentExpiresAt,
        String entryQrToken
) {
}
