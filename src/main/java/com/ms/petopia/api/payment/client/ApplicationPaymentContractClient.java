package com.ms.petopia.api.payment.client;

import com.ms.petopia.api.payment.dto.ApplicationVendorFeePaymentContext;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 참가업체(신청) 도메인이 결제 도메인을 위해 열어둔 참가비 결제 내부 계약을 호출하는 클라이언트.
 * {@link FairOpeningFeePaymentContractClient}와 같은 역할·같은 구조다 - 같은 애플리케이션 안이지만
 * (모놀리스) 도메인 간 직접 자바 참조 대신 참가업체 쪽이 REST로 노출한 계약을 그대로 따른다.
 */
@Component
public class ApplicationPaymentContractClient {

    // TODO 인증 도메인 확정되면 이 임시 헤더 대신 내부 인증으로 교체
    private static final String INTERNAL_CALLER_HEADER = "X-Internal-Caller";
    private static final String PAYMENT_CALLER_VALUE = "PAYMENT";

    private final RestClient restClient;

    public ApplicationPaymentContractClient(
            @Value("${petopia.application.internal-base-url:http://localhost:8080}") String baseUrl
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
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 신청이 존재하지 않거나
     *         참가비 결제 가능한 상태가 아닐 때(참가업체 도메인이 4xx로 거부한 경우)
     * @throws CommonException {@link ErrorCode#PAYMENT_GATEWAY_UNAVAILABLE} 참가업체 도메인이 5xx를
     *         반환했거나(그쪽 장애) 연결 자체가 안 됐을 때(타임아웃 포함) - 4xx 업무 거부와 달리
     *         이건 우리 쪽 문제가 아니라서 "결제 불가"(409)가 아니라 "일시적으로 이용 불가"(503)로
     *         구분한다
     */
    public ApplicationVendorFeePaymentContext getPaymentContext(Long applicationId) {
        try {
            return restClient.get()
                    .uri("/applications/{applicationId}/vendor-fee-payment-context", applicationId)
                    .retrieve()
                    .body(ApplicationVendorFeePaymentContext.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE, e);
            }
            throw new CommonException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, e);
        } catch (ResourceAccessException e) {
            throw new CommonException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, e);
        }
    }
}
