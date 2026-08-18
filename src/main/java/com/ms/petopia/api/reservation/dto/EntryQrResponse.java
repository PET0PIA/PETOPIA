package com.ms.petopia.api.reservation.dto;

public record EntryQrResponse(
        Long reservationId,
        String qrToken
) {
}
