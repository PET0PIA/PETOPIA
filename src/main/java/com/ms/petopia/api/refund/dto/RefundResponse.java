package com.ms.petopia.api.refund.dto;

import java.time.LocalDateTime;

/**
 * 환불 단건 응답.
 *
 * @param refundId           환불 PK
 * @param paymentId           환불 대상 결제 PK
 * @param refundReason        환불 사유(5경로)
 * @param requestedByDomain   환불을 촉발한 도메인
 * @param refundAmount        환불 금액(MVP는 결제 전액과 동일)
 * @param status               환불 상태(MVP는 REQUESTED 없이 바로 COMPLETED — 모의 환불)
 * @param requestedAt          환불 요청 시각
 * @param processedAt          환불 처리 완료 시각
 */
public record RefundResponse(
        Long refundId,
        Long paymentId,
        String refundReason,
        String requestedByDomain,
        Long refundAmount,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {

    public static RefundResponse from(RefundRow row) {
        return new RefundResponse(
                row.getRefundId(),
                row.getPaymentId(),
                row.getRefundReason(),
                row.getRequestedByDomain(),
                row.getRefundAmount(),
                row.getStatus(),
                row.getRequestedAt(),
                row.getProcessedAt()
        );
    }
}
