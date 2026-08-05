package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.dto.request.NtsValidateRequest;
import com.ms.petopia.api.business.dto.response.NtsValidateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class NtsBusinessVerificationClient {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final RestClient restClient;   // baseUrl 안 씀, 아래서 절대경로로 직접 만듦
    private final String serviceKey;

    public NtsBusinessVerificationClient(@Value("${nts.service-key}") String serviceKey) {

        this.serviceKey = serviceKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000); // 3초 안에 연결 안 되면 포기
        requestFactory.setReadTimeout(5000); // 5초 안에 응답 안 오면 포기

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

    }

    public boolean validate(String bizRegNo, String ceoName, LocalDate startDate) {

        NtsValidateRequest.BusinessItem item = NtsValidateRequest.BusinessItem.builder()
                .bNo(bizRegNo)
                .startDt(startDate.format(YYYYMMDD))
                .pNm(ceoName)
                .build();

        /*
         * URI.create()는 이미 인코딩된 문자열(%2B, %3D%3D 등)을 절대 다시 인코딩하지 않는다.
         * RestClient.uri(String)을 쓰면 Spring이 재인코딩을 시도해 이중 인코딩이 되므로,
         * 반드시 URI 객체로 만들어서 넘긴다.
         */
        URI uri = URI.create("https://api.odcloud.kr/api/nts-businessman/v1/validate?serviceKey="
                        + serviceKey + "&returnType=JSON");

        try {

            NtsValidateResponse response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new NtsValidateRequest(List.of(item)))
                    .retrieve()
                    .body(NtsValidateResponse.class);

            if(response == null || response.getData() == null || response.getData().isEmpty()) {
                throw new IllegalStateException("국세청 API 응답이 비어있습니다.");
            }

            NtsValidateResponse.ValidCode valid = response.getData().get(0).getValid();

            // 방어 코드
            if(valid == null) {
                throw new IllegalStateException("국세청 API가 알 수 없는 응답 코드를 반환했습니다.");
            }

            return valid == NtsValidateResponse.ValidCode.MATCH;

        } catch(Exception e) {

            /*
             * 예외 메시지에 serviceKey가 그대로 포함될 수 있어(URL 쿼리파라미터),
             * 마스킹된 메시지만 로그에 남긴다.
             * cause(e)를 그대로 재던지지 않는 이유: 호출부가 이 예외를 어떻게 처리하든
             * (cause를 유지한 채 다시 던지거나 로깅하더라도) 원본 예외 안의 serviceKey가
             * 노출될 여지를 원천 차단하기 위함.
             */
            log.error("국세청 진위확인 API 호출 실패: {}", maskServiceKey(e.getMessage()));
            throw new IllegalStateException("국세청 API 호출 실패");

        }

    }

    // 로그에 실제 serviceKey 값이 남지 않도록 마스킹한다.
    private String maskServiceKey(String message) {
        if (message == null || serviceKey == null || serviceKey.isBlank()) {
            return message;
        }
        return message.replace(serviceKey, "***MASKED***");
    }

}
