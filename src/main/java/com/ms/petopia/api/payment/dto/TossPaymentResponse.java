package com.ms.petopia.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * 토스페이먼츠 결제승인(confirm) API 응답 중 우리가 쓰는 필드만 매핑.
 * 토스가 이 외에도 카드정보 등 훨씬 많은 필드를 내려주지만 다 안 씀.
 *
 * @param status "DONE"(승인 완료) 또는 "WAITING_FOR_DEPOSIT"(가상계좌 발급, 입금 대기).
 *               PaymentService.confirmPayment가 이 값을 보고 COMPLETED로 바로 확정할지,
 *               WAITING_FOR_DEPOSIT으로 남겨둘지 분기한다 — 상태를 안 보고 무조건 COMPLETED로
 *               확정하면 실제로는 입금 전인 가상계좌 결제도 완료로 잘못 표시된다.
 * @param easyPay 간편결제(네이버페이 등)로 결제했을 때만 채워짐. 카드/계좌이체 등 일반
 *                결제면 null.
 * @param virtualAccount 가상계좌로 결제했을 때만 채워짐. 그 외 결제수단이면 null.
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

    /**
     * @param provider 간편결제 제공사(예: "네이버페이", "토스페이"). payment.easy_pay_provider에 그대로 저장한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EasyPay(String provider) {
    }

    /**
     * dueDate는 예전엔 오프셋 없는 포맷이라고 알려져 LocalDateTime으로 받았는데, 실제로는
     * 오프셋이 붙은 "2026-08-23T16:39:16+09:00" 형태로 내려와서 Jackson 파싱이 터졌다(가상계좌
     * confirm 500 에러, 2026-08-22 실결제 테스트로 확인) — OffsetDateTime으로 받고, 저장할 때
     * PaymentService가 .toLocalDateTime()으로 변환한다(서버·DB가 전부 Asia/Seoul 고정이라 오프셋을
     * 버려도 값이 어긋나지 않음, approvedAt과 같은 이유로 OffsetDateTime을 씀).
     *
     * @param secret 입금 웹훅(POST /webhooks/toss/deposit-callback) body의 secret과 대조해서
     *               위조를 방지하는 값. payment.virtual_account_secret에 저장하고, API 응답으론
     *               절대 내보내지 않는다(PaymentResponse에 이 필드 없음).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VirtualAccount(
            String bankCode,
            String accountNumber,
            OffsetDateTime dueDate,
            String secret
    ) {
    }
}
