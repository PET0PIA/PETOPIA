package com.ms.petopia.api.payment.dto;

import java.time.LocalDateTime;

/**
 * 참가업체 도메인의 참가비 결제 컨텍스트 조회(GET .../vendor-fee-payment-context) 응답을 그대로 매핑.
 * {@link ReservationPaymentContext}/{@link FairOpeningFeePaymentContext}와 같은 역할이다.
 */
public record ApplicationVendorFeePaymentContext(
        Long applicationId,
        Long fairId,
        Long businessId,
        Long payerUserId,
        long amount,
        LocalDateTime paymentDueAt
) {
}
