package com.ms.petopia.api.business.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 국세청 사업자등록정보 진위확인 API 요청
@Getter
@AllArgsConstructor
public class NtsValidateRequest {

    private List<BusinessItem> businesses; // 여러 개 사업자를 담는 리스트

    @Getter
    @Builder
    @AllArgsConstructor
    public static class BusinessItem {

        @JsonProperty("b_no")
        private String bNo;

        @JsonProperty("start_dt")
        private String startDt;

        @JsonProperty("p_nm")
        private String pNm;

    }

}
