package com.ms.petopia.api.business.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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


    /*
     * 사업자 진위확인 상태.
     * 현재 등록 로직(registerBusiness)에서는 검증 실패 시 등록 자체를 막기 때문에
     * 실제로는 VERIFIED만 저장된다. 나머지 값들은 향후 재검증/관리자 수동 처리 기능
     * 등에서 다시 쓰일 수 있어 남겨둔다.
     */
    public enum VerifyStatus {
        PENDING, VERIFIED, INVALID, RETRY_NEEDED
    }

}
