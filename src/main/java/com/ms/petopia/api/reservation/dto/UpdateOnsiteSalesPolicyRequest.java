package com.ms.petopia.api.reservation.dto;

public record UpdateOnsiteSalesPolicyRequest(
        Long price,
        String status,
        Integer expectedVersion
) {
}
