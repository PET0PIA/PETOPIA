package com.ms.petopia.api.payment.dto;

import java.time.LocalDateTime;

/** 행사 도메인의 개설비 결제 계약 조회(GET .../opening-fee-payment-context) 응답을 그대로 매핑. */
public record FairOpeningFeePaymentContext(
        Long fairId,
        long amount,
        LocalDateTime paymentDueAt
) {
}
