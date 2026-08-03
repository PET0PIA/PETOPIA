package com.ms.petopia.api.reservation.dto;

import java.util.List;

public record ReservationListResponse(
        List<ReservationListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
