package com.ms.petopia.api.reservation.dto;

import java.util.List;

public record AdminReservationListResponse(
        List<AdminReservationItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
