package com.ms.petopia.api.refund.dto;

import java.time.LocalDateTime;

/**
 * 환불 단건 응답.
 *
 * @param refundId           환불 PK
 * @param paymentId           환불 대상 결제 PK
 * @param reservationId       환불 대상 결제가 예약금 결제일 때 그 예약 PK(참가비 환불이면 null).
 *                            예약 도메인이 이 응답만 보고 바로 어떤 예약 건인지 알 수 있게 하려고 넣음.
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
        Long reservationId,
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
                row.getReservationId(),
                row.getRefundReason(),
                row.getRequestedByDomain(),
                row.getRefundAmount(),
                row.getStatus(),
                row.getRequestedAt(),
                row.getProcessedAt()
        );
    }
}
