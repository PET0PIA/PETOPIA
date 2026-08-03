package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;

public record UpdateReservationVisitDateRequest(
        LocalDate visitDate
) {
}
