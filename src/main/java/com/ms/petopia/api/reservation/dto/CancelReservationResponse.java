package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

public record CancelReservationResponse(
        Long reservationId,
        String reservationStatus,
        LocalDateTime canceledAt
) {
}
