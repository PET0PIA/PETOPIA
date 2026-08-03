package com.ms.petopia.api.payment.client;

import com.ms.petopia.api.payment.dto.TossPaymentResponse;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TossPaymentClient {

    private final RestClient restClient;

    public TossPaymentClient(
            RestClient.Builder restClientBuilder,
            @Value("${petopia.toss.secret-key}") String secretKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.tosspayments.com/v1")
                .defaultHeaders(headers -> headers.setBasicAuth(secretKey, ""))
                .build();
    }

    /**
     * 결제 승인. paymentKey/orderId/amount는 프론트가 토스 리다이렉트에서 받아온 값을
     * 그대로 전달하되, amount는 호출자(PaymentService)가 자기 DB에 저장해둔
     * 금액이어야 한다 — 프론트가 보낸 값을 검증 없이 그대로 넘기면 안 된다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_APPROVAL_FAILED} 토스가 승인을 거부했을 때
     */
    public TossPaymentResponse confirmPayment(String paymentKey, String orderId, Long amount) {
        try {
            return restClient.post()
                    .uri("/payments/confirm")
                    .body(new ConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPaymentResponse.class);
        } catch (RestClientResponseException e) {
            throw new CommonException(
                    ErrorCode.PAYMENT_APPROVAL_FAILED,
                    "토스 승인 실패: " + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    private record ConfirmRequest(String paymentKey, String orderId, Long amount) {
    }
}
