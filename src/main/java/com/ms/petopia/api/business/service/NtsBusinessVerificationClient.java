package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.dto.request.NtsValidateRequest;
import com.ms.petopia.api.business.dto.response.NtsValidateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    private final RestClient restClient = RestClient.create();   // baseUrl 안 씀, 아래서 절대경로로 직접 만듦

    @Value("${nts.service-key}")
    private String serviceKey;

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

            return response.getData().get(0).getValid() == NtsValidateResponse.ValidCode.MATCH;

        } catch(Exception e) {

            log.error("국세청 진위확인 API 호출 실패", e);
            throw new IllegalStateException("국세청 API 호출 실패", e);

        }

    }

}
