package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 행사 취소 신청 검토 결과.
 *
 * @param fairCancelRequestId 검토한 취소 신청 PK
 * @param fairId               대상 행사 PK
 * @param status               승인 시 APPROVED, 반려 시 REJECTED
 * @param reviewedAt           검토 처리 일시
 * @param rejectReason         반려 시 사유. 승인이면 null
 * @param canceledAt           승인 시 fairs.canceled_at에 채워진 값(=reviewedAt). 반려면 null
 */
public record ReviewFairCancelRequestResponse(
        Long fairCancelRequestId,
        Long fairId,
        String status,
        LocalDateTime reviewedAt,
        String rejectReason,
        LocalDateTime canceledAt
) {
}
