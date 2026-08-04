package com.ms.petopia.api.payment.client;

import com.ms.petopia.api.payment.dto.TossPaymentResponse;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Component
public class TossPaymentClient {

    private final RestClient restClient;

    public TossPaymentClient(@Value("${petopia.toss.secret-key}") String secretKey) {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.tosspayments.com/v1")
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(secretKey, ""))
                .build();
    }

    /**
     * 결제 승인. paymentKey/orderId/amount는 프론트가 토스 리다이렉트에서 받아온 값을
     * 그대로 전달하되, amount는 호출자(PaymentService)가 자기 DB에 저장해둔
     * 금액이어야 한다 — 프론트가 보낸 값을 검증 없이 그대로 넘기면 안 된다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_APPROVAL_FAILED} 토스가 승인을 거부했을 때(4xx)
     * @throws CommonException {@link ErrorCode#PAYMENT_GATEWAY_UNAVAILABLE} 토스 서버 자체 장애일 때(5xx)
     */
    public TossPaymentResponse confirmPayment(String paymentKey, String orderId, Long amount) {
        try {
            return restClient.post()
                    .uri("/payments/confirm")
                    .body(new ConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPaymentResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx: 토스가 승인 자체를 거부함(카드 문제, amount 불일치 등) — 재시도해도 똑같이 실패함.
            // 원본 응답 본문은 로그에만 남기고, 클라이언트한테는 일반화된 문구만 준다.
            log.warn("토스 승인 거부: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CommonException(ErrorCode.PAYMENT_APPROVAL_FAILED, "결제 승인에 실패했습니다.", e);
        } catch (HttpServerErrorException e) {
            // 5xx: 토스 서버 쪽 장애 — 우리 요청이 잘못된 게 아니라 나중에 재시도하면 될 수 있는 문제.
            log.error("토스 서버 오류: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CommonException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, e);
        }
    }

    private record ConfirmRequest(String paymentKey, String orderId, Long amount) {
    }
}
