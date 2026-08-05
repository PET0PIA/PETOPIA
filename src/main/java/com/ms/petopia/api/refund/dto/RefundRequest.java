package com.ms.petopia.api.refund.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 환불 요청 수신 API의 요청 바디.
 *
 * @param refundReason        USER_CANCEL / FAIR_CANCEL_USER / VENDOR_CANCEL / FAIR_CANCEL_VENDOR / OPENING_FEE_MANUAL
 * @param requestedByDomain   환불을 촉발한 도메인(예: "RESERVATION", "FAIR", "VENDOR", "PAYMENT_ADMIN"). 감사·추적용.
 */
public record RefundRequest(
        @NotBlank String refundReason,
        @NotBlank String requestedByDomain
) {
}
