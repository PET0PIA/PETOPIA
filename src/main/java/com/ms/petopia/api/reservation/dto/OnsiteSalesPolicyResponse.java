package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record OnsiteSalesPolicyResponse(
        Long fairId,
        Long fairDateId,
        LocalDate operationDate,
        long price,
        String status,
        int version,
        LocalDateTime updatedAt
) {
}
