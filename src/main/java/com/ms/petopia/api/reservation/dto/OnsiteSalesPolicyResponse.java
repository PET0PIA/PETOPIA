package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 운영일별 현장예매 정책. 관리자 화면이 그대로 그린다.
 *
 * @param capacity      현장예매 전용 정원. null이면 제한 없음
 * @param reservedCount 그 정원을 점유 중인 현장예매 수(읽기 전용)
 */
public record OnsiteSalesPolicyResponse(
        Long fairId,
        Long fairDateId,
        LocalDate operationDate,
        long price,
        Integer capacity,
        int reservedCount,
        String status,
        int version,
        LocalDateTime updatedAt
) {
}
