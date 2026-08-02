package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationListItemResponse(
        Long reservationId,
        String fairName,
        String fairPosterImageUrl,
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        String reservationStatus,
        boolean isEnded,
        boolean qrAvailable,
        long amount,
        LocalDateTime reservedAt,
        LocalDateTime checkedInAt
) {
}
