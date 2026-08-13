package com.ms.petopia.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스페이먼츠가 우리 웹훅 엔드포인트로 보내는 이벤트 바디 중 가상계좌 입금통지
 * (eventType="DEPOSIT_CALLBACK")에 필요한 필드만 매핑한다. 다른 eventType(결제상태변경 등)도
 * 같은 엔드포인트로 들어올 수 있어 eventType으로 먼저 걸러야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossWebhookEvent(String eventType, Data data) {

    /**
     * @param status  가상계좌 입금 상태("DONE"=입금완료, "CANCELED"=입금기한만료/취소, 그 외는 무시)
     * @param secret  이 결제 건 발급 시점에 우리가 저장해둔 virtualAccount.secret과 대조해서
     *                진짜 토스가 보낸 웹훅인지 검증하는 값(토스 문서 기준 가상계좌 웹훅 공식 검증 방식).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String orderId, String status, String secret, String transactionKey) {
    }
}
