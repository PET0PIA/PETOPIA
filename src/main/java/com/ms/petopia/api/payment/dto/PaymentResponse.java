package com.ms.petopia.api.payment.dto;

import java.time.LocalDateTime;

/**
 * 결제 단건 조회 응답.
 *
 * <p>{@link PaymentRow}(DB 로우 그대로)를 API 응답으로 바로 내보내지 않고 이 record로
 * 한 번 감싸는 이유: Row는 세터가 열려 있는 mutable 객체라 응답 계약(스펙)으로 쓰기엔
 * 안전하지 않고, 나중에 응답 전용 필드(예: 표시용 라벨)를 추가할 때도 DB 매핑과 분리돼 있어야
 * Row를 건드리지 않고 확장할 수 있다.
 *
 * @param paymentId    결제 PK
 * @param paymentType  결제 유형 (RESERVATION_DEPOSIT / VENDOR_FEE / FAIR_OPENING_FEE)
 * @param amount       결제 금액 (원화 정수단위)
 * @param status       결제 상태
 * @param method       결제 수단 (MVP 기준 "MOCK" 고정)
 * @param paidAt       결제 완료 시각 (미결제 상태면 null일 수 있음)
 * @param createdAt    결제 요청 생성 시각
 * @param fairId       행사 ID
 * @param businessId   참가업체(사업자) ID, 관람객 예약금 결제일 땐 null
 * @param payerUserId  결제자 USER ID
 * @param reservationId 예약 ID (예약금 결제일 때만 값 있음)
 * @param applicationId 참가신청 ID (참가비 결제일 때만 값 있음)
 */
public record PaymentResponse(
        Long paymentId,
        String orderId,
        String paymentType,
        Long amount,
        String status,
        String method,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        Long fairId,
        Long businessId,
        Long payerUserId,
        Long reservationId,
        Long applicationId
) {

    /**
     * {@link PaymentRow}를 응답 DTO로 변환한다.
     * 변환 로직을 record 안에 둬서, 서비스 코드에서는 매번 필드를 나열하지 않고 한 줄로 쓸 수 있게 한다.
     */
    public static PaymentResponse from(PaymentRow row) {
        return new PaymentResponse(
                row.getPaymentId(),
                "PAYMENT_" + row.getPaymentId(),
                row.getPaymentType(),
                row.getAmount(),
                row.getStatus(),
                row.getMethod(),
                row.getPaidAt(),
                row.getCreatedAt(),
                row.getFairId(),
                row.getBusinessId(),
                row.getPayerUserId(),
                row.getReservationId(),
                row.getApplicationId()
        );
    }
}
