package com.ms.petopia.api.business.dto.response;

import com.ms.petopia.api.business.domain.Business;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 사업자 조회/등록 응답 공통 DTO
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessResponse {

    private Long businessId;
    private String name;
    private String ceoName;
    private String bizRegNo;
    private LocalDate startDate;
    private String address;
    private String phone;
    private String website;
    private String verifyStatus;
    private LocalDateTime createdAt;

    // Business 도메인 객체를 API 응답용 BusinessResponse로 변환
    public static BusinessResponse from(Business business) {

        return BusinessResponse.builder()
                .businessId(business.getBusinessId())
                .name(business.getName())
                .ceoName(business.getCeoName())
                .bizRegNo(business.getBizRegNo())
                .startDate(business.getStartDate())
                .address(business.getAddress())
                .phone(business.getPhone())
                .website(business.getWebsite())
                // enum 타입이지만 응답 DTO에서는 String으로 내려줘야 하므로 .name()으로 변환
                .verifyStatus(business.getVerifyStatus().name())
                .createdAt(business.getCreatedAt())
                .build();

    }

}