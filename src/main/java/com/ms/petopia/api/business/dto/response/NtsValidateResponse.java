package com.ms.petopia.api.business.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// 국세청 사업자등록정보 진위확인 API 응답
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NtsValidateResponse {

    @JsonProperty("request_cnt")
    private int requestCnt; // 조회 요청 수

    @JsonProperty("valid_cnt")
    private int validCnt; // 검증 Valid 수

    @JsonProperty("status_code")
    private String statusCode;

    private List<Result> data;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Result {

        @JsonProperty("b_no")
        private String bNo; // 사업자등록번호

        private ValidCode valid; // 진위확인 결과 코드(01: Valid, 02: Invalid)

        @JsonProperty("valid_msg")
        private String validMsg; // 진위확인 결과 메세지

    }

    /*
     * 국세청 진위확인 결과 코드.
     * enum 상수에 @JsonProperty를 직접 붙여서 "01"/"02" 문자열과 매핑한다.
     */
    public enum ValidCode {
        @JsonProperty("01") MATCH,
        @JsonProperty("02") MISMATCH
    }

}
