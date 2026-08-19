package com.ms.petopia.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스페이먼츠 웹훅(POST /webhooks/toss/deposit-callback) 요청 바디. 토스는 결제상태변경 등
 * 다른 이벤트 타입도 같은 채널로 보낼 수 있어 eventType으로 걸러야 한다
 * (PaymentService.handleDepositWebhook는 "DEPOSIT_CALLBACK"만 처리하고 나머지는 무시한다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossWebhookEvent(String eventType, Data data) {

    /**
     * @param status "DONE"(입금완료) 또는 "CANCELED"(입금기한 만료 등으로 가상계좌 취소)
     * @param secret confirm 응답에서 저장해둔 payment.virtual_account_secret과 대조해서
     *               위조를 막는 값(토스 공식 검증방식)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String orderId, String status, String secret) {
    }
}
