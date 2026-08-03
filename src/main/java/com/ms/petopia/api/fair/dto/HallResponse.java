package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 홀 조회/생성/수정 응답.
 */
public record HallResponse(
        Long hallId,
        Long fairId,
        String name,
        String floorPlanImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
