package com.ms.petopia.api.business.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Business {

    private Long businessId;
    private Long ownerId;
    private String name; // 업체명(상호명)
    private String ceoName;
    private String bizRegNo; // 사업자등록번호
    private LocalDate startDate;
    private String address;
    private String phone;
    private String website;
    private VerifyStatus verifyStatus; // 사업자 확인 결과
    private LocalDateTime createdAt;


    public enum VerifyStatus {
        PENDING, VERIFIED, INVALID, RETRY_NEEDED
    }

}
