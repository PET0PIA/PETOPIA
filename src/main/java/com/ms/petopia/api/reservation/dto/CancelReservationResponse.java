package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

/**
 * 예약 취소 결과.
 *
 * <p>환불 필드는 "결제까지 끝난 예약금을 환불한 경우"에만 채워진다. 무료 예약이나 결제 전
 * (PENDING_PAYMENT) 예약 취소는 환불할 돈 자체가 없어서 {@code refunded=false}에 나머지는 null이다
 * (결제 전 예약에 딸린 PENDING 결제는 취소만 하고 환불 원장을 만들지 않는다).
 *
 * @param reservationId      취소된 예약 PK
 * @param reservationStatus  취소 후 예약 상태(항상 CANCELED)
 * @param canceledAt         취소 처리 시각
 * @param refunded           예약금 환불이 함께 처리됐는지
 * @param refundId           환불 PK(환불이 없으면 null)
 * @param refundAmount       환불 금액. MVP는 결제 전액 환불이라 결제금액과 같다(환불이 없으면 null).
 * @param refundStatus       환불 상태. 모의 환불이라 접수와 동시에 COMPLETED다(환불이 없으면 null).
 */
public record CancelReservationResponse(
        Long reservationId,
        String reservationStatus,
        LocalDateTime canceledAt,
        boolean refunded,
        Long refundId,
        Long refundAmount,
        String refundStatus
) {
}
