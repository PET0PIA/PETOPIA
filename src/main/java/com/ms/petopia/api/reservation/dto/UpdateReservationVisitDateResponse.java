package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateReservationVisitDateResponse(
        Long reservationId,
        LocalDate previousVisitDate,
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        String reservationStatus
) {
}
