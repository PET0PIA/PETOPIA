package com.ms.petopia.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 토스페이먼츠 결제승인(confirm) API 응답 중 우리가 쓰는 필드만 매핑.
 * 토스가 이 외에도 카드정보 등 훨씬 많은 필드를 내려주지만 다 안 씀.
 *
 * @param status 토스가 매기는 결제 상태("DONE"이 정상 완료). 가상계좌는 발급 시점에 입금 전이라
 *               "WAITING_FOR_DEPOSIT"로 내려온다 — PaymentService.confirmPayment가 이 값을 보고
 *               COMPLETED 대신 WAITING_FOR_DEPOSIT으로 분기한다.
 * @param easyPay 간편결제(네이버페이 등)로 결제됐을 때만 값이 있다. 일반 카드/가상계좌는 null.
 * @param virtualAccount 가상계좌로 결제됐을 때만 값이 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        Long totalAmount,
        String method,
        OffsetDateTime approvedAt,
        EasyPay easyPay,
        VirtualAccount virtualAccount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EasyPay(String provider) {
    }

    /**
     * @param dueDate 토스 문서 기준 {@code yyyy-MM-dd'T'HH:mm:ss} 포맷 — approvedAt 등 다른
     *               시각 필드와 달리 타임존 오프셋이 없다. {@link OffsetDateTime}으로 받으면
     *               Jackson이 파싱에 실패해 가상계좌 결제 confirm 자체가 깨진다(CodeRabbit 지적,
     *               토스 개발자센터 문서로 확인).
     * @param secret 이 가상계좌 건의 입금통지 웹훅(DEPOSIT_CALLBACK)에 그대로 실려오는 값 —
     *               웹훅 body의 secret과 여기서 받은 값이 같아야 정상 웹훅으로 검증한다
     *               (토스 문서 기준 가상계좌 웹훅의 공식 검증 방식, HMAC 서명이 아님).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VirtualAccount(String bankCode, String accountNumber, LocalDateTime dueDate, String secret) {
    }
}
