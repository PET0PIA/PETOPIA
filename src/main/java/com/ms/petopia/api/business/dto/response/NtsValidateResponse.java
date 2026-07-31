package com.ms.petopia.api.business.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// 국세청 사업자등록정보 진위확인 API 응답
@Getter
@Setter
@NoArgsConstructor
public class NtsValidateResponse {

    @JsonProperty("request_cnt")
    private int requestCnt;

    @JsonProperty("valid_cnt")
    private int validCnt;

    private List<Result> data;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Result {

        @JsonProperty("b_no")
        private String bNo;

        private String valid;

        @JsonProperty("valid_msg")
        private String validMsg;

    }

}
