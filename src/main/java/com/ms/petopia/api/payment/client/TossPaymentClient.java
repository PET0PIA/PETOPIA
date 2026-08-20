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

    /**
     * 아직 입금 전(WAITING_FOR_DEPOSIT)인 가상계좌를 실제로 닫는다. 토스 공식 문서 기준
     * 결제취소 API(POST /payments/{paymentKey}/cancel)를 입금 전 가상계좌에 호출하면
     * 환불할 금액이 없어 refundReceiveAccount 없이도 계좌가 닫히고 상태가 CANCELED로
     * 바뀐다(이미 입금된 뒤의 취소·환불과는 다른 경로).
     *
     * <p>우리 쪽 로컬 상태(CANCELED/EXPIRED)와 실제 은행 계좌 상태를 맞추기 위한 호출이라,
     * 여기서 실패하면 로컬 상태도 바꾸지 않는다({@link com.ms.petopia.api.payment.service.PaymentService}
     * 호출부 참고) — 계좌가 실제로는 안 닫혔는데 우리 DB만 취소로 표시되면, 그 사이 들어온
     * 입금을 아무도 자동으로 못 잡아내는 known limitation이 재발하기 때문이다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_CANCELLATION_FAILED} 토스가 취소를 거부했을 때(4xx)
     * @throws CommonException {@link ErrorCode#PAYMENT_GATEWAY_UNAVAILABLE} 토스 서버 자체 장애일 때(5xx)
     */
    public void cancelVirtualAccount(String paymentKey, String cancelReason) {
        try {
            restClient.post()
                    .uri("/payments/{paymentKey}/cancel", paymentKey)
                    .body(new CancelRequest(cancelReason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            log.warn("토스 가상계좌 취소 거부: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CommonException(ErrorCode.PAYMENT_CANCELLATION_FAILED, "가상계좌 취소에 실패했습니다.", e);
        } catch (HttpServerErrorException e) {
            log.error("토스 서버 오류(가상계좌 취소): status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CommonException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, e);
        }
    }

    private record ConfirmRequest(String paymentKey, String orderId, Long amount) {
    }

    private record CancelRequest(String cancelReason) {
    }
}
