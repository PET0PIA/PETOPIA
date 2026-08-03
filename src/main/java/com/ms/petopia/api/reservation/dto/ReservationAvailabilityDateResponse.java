package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationAvailabilityDateResponse(
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        long remainingCapacity,
        boolean available
) {
}
