package com.ms.petopia.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * 토스페이먼츠 결제승인(confirm) API 응답 중 우리가 쓰는 필드만 매핑.
 * 토스가 이 외에도 카드정보 등 훨씬 많은 필드를 내려주지만 다 안 씀.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        Long totalAmount,
        String method,
        OffsetDateTime approvedAt
) {
}
