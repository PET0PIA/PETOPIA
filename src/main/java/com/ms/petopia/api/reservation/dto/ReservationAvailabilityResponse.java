package com.ms.petopia.api.reservation.dto;

import java.util.List;

public record ReservationAvailabilityResponse(
        Long fairId,
        long reservationFee,
        List<ReservationAvailabilityDateResponse> dates
) {
}
