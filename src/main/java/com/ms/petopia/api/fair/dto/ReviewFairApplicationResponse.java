package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 행사 신청 검토 결과.
 *
 * @param fairId           검토한 신청서(=행사) PK
 * @param status           승인 시 PAYMENT_PENDING, 반려 시 REJECTED
 * @param reviewedAt       검토 처리 일시
 * @param openingFeeAmount 승인 시 확정된 개설비 금액(원). 반려면 null
 * @param paymentDueAt     승인 시 개설비 결제 기한. 반려면 null
 * @param rejectReason     반려 시 사유. 승인이면 null
 */
public record ReviewFairApplicationResponse(
        Long fairId,
        String status,
        LocalDateTime reviewedAt,
        Long openingFeeAmount,
        LocalDateTime paymentDueAt,
        String rejectReason
) {
}
