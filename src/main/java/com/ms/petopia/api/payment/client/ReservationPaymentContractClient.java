package com.ms.petopia.api.payment.client;

import com.ms.petopia.api.payment.dto.ReservationPaymentCompletionResult;
import com.ms.petopia.api.payment.dto.ReservationPaymentContext;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 예약 도메인이 결제 도메인을 위해 열어둔 내부 계약 API를 호출하는 클라이언트.
 * 같은 애플리케이션 안이지만(모놀리스), 도메인 간 직접 자바 참조 대신
 * 예약 쪽이 REST로 노출한 계약을 그대로 따른다.
 */

@Component
public class ReservationPaymentContractClient {

    // TODO 인증 도메인 확정되면 이 임시 헤더 대신 내부 인증으로 교체
    private static final String INTERNAL_CALLER_HEADER = "X-Internal-Caller";
    private static final String PAYMENT_CALLER_VALUE = "PAYMENT";

    private final RestClient restClient;

    public ReservationPaymentContractClient(
            @Value("${petopia.reservation.internal-base-url:http://localhost:8080}") String baseUrl
    ) {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + "/internal/api/v1")
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.set(INTERNAL_CALLER_HEADER, PAYMENT_CALLER_VALUE))
                .build();
    }

    /**
     *  @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 예약이 존재하지 않거나
     *         결제 가능한 상태가 아닐 때(예약 도메인이 4xx로 거부한 경우 전부 이걸로 통일)
     */
    public ReservationPaymentContext getPaymentContext(Long reservationId) {
        try {
            return restClient.get()
                    .uri("/reservations/{reservationId}/payment-context", reservationId)
                    .retrieve()
                    .body(ReservationPaymentContext.class);
        } catch (RestClientResponseException e) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE, e);
        }
    }


    /**
     * 결제 완료를 예약 도메인에 통지한다. eventId로 멱등 처리되므로 재시도해도 안전하다.
     */
    public ReservationPaymentCompletionResult completePayment(
            String eventId, Long paymentId, Long reservationId, Long paidAmount, LocalDateTime paidAt
    ) {
        return restClient.post()
                .uri("/reservation-payment-completions")
                .body(new CompletionRequest(eventId, paymentId, reservationId, paidAmount, paidAt))
                .retrieve()
                .body(ReservationPaymentCompletionResult.class);
    }

    private record CompletionRequest(
            String eventId, Long paymentId, Long reservationId, Long paidAmount, LocalDateTime paidAt
    ) {
    }

}
