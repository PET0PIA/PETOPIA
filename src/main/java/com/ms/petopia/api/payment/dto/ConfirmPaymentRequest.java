package com.ms.petopia.api.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 결제 승인 확정 요청. 토스 결제창 완료 후 프론트가 리다이렉트에서 받은 paymentKey만 넘긴다.
 * orderId(경로변수 paymentId로 역산)와 amount(DB에 저장된 값)는 서버가 자체적으로 판단한다.
 */
public record ConfirmPaymentRequest(
        @NotBlank String paymentKey
) {
}
