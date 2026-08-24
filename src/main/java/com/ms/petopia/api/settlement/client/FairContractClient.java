package com.ms.petopia.api.settlement.client;

import com.ms.petopia.api.settlement.dto.FairCancellationStatus;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 행사 도메인이 결제/정산 도메인을 위해 열어둔 내부 계약 API를 호출하는 클라이언트.
 * {@code ReservationPaymentContractClient}와 같은 패턴 - 같은 애플리케이션 안(모놀리스)이지만
 * 도메인 간 직접 자바 참조 대신 행사 쪽이 REST로 노출한 계약을 그대로 따른다.
 */
@Component
public class FairContractClient {

    // TODO 인증 도메인 확정되면 이 임시 헤더 대신 내부 인증으로 교체
    private static final String INTERNAL_CALLER_HEADER = "X-Internal-Caller";
    private static final String PAYMENT_CALLER_VALUE = "PAYMENT";

    private final RestClient restClient;

    public FairContractClient(
            @Value("${petopia.fair.internal-base-url:http://localhost:8080}") String baseUrl
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
     * 행사 취소 여부를 조회한다. 정산 계산·확정 직전, 그리고 존재하지 않는 행사 ID를 걸러내는
     * 용도로도 호출한다(2026-08-24, getByFairId 조회에서 "행사 없음"과 "정산 미계산"을
     * 구분하려고 추가) - 이 내부 엔드포인트의 유일한 4xx 실패 사유가 FAIR_NOT_FOUND라
     * ReservationPaymentContractClient와 같은 패턴으로 하나의 에러코드로 통일한다.
     *
     * <p>호출 실패(네트워크/타임아웃/5xx 등)는 그대로 전파한다 - 실패를 "취소 안 됨"으로
     * 임의 간주해버리면 실제로 취소된 행사인데 정산이 진행될 위험이 있어서, 조용히
     * 삼키지 않고 요청 자체를 실패시키는 쪽이 더 안전한 기본값이다.
     *
     * @throws CommonException {@link ErrorCode#FAIR_NOT_FOUND} 존재하지 않는 행사 ID일 때
     */
    public FairCancellationStatus getCancellationStatus(Long fairId) {
        try {
            return restClient.get()
                    .uri("/fairs/{fairId}/cancellation-status", fairId)
                    .retrieve()
                    .body(FairCancellationStatus.class);
        } catch (RestClientResponseException e) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND, e);
        }
    }
}
