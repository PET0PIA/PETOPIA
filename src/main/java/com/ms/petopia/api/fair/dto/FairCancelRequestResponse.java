package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 취소 신청 등록/조회 응답.
 */
public record FairCancelRequestResponse(
        Long fairCancelRequestId,
        Long fairId,
        Long requestedBy,
        String reason,
        String status,
        String rejectReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
}
