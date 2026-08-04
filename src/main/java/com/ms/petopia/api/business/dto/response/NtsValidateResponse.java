package com.ms.petopia.api.business.dto.response;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
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
     * "01"=일치, "02"=불일치. 그 외(빈 문자열, 새로운 코드 등)는 UNKNOWN으로 처리한다.
     * @JsonEnumDefaultValue + READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE 설정으로,
     * 매핑 안 되는 값이 와도 예외 대신 UNKNOWN으로 안전하게 떨어지게 한다.
     */
    public enum ValidCode {
        @JsonProperty("01") MATCH,
        @JsonProperty("02") MISMATCH,
        @JsonEnumDefaultValue UNKNOWN
    }

}
