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
    private String businessRegDocKey; // 사업자등록증 첨부파일 S3 objectKey
    private ApprovalStatus approvalStatus; // 관리자 심사 상태
    private String rejectReason; // 반려/취소 사유
    private Long reviewedBy; // 심사한 SUPER_ADMIN users.user_id
    private LocalDateTime reviewedAt; // 심사 처리 일시
    private LocalDateTime createdAt;


    /*
     * 사업자 진위확인 상태.
     * 현재 등록 로직(registerBusiness)에서는 검증 실패 시 등록 자체를 막기 때문에
     * 실제로는 VERIFIED만 저장된다. 나머지 값들은 향후 재검증 기능
     * 등에서 다시 쓰일 수 있어 남겨둔다.
     */
    public enum VerifyStatus {
        PENDING, VERIFIED, INVALID, RETRY_NEEDED
    }

    /*
     * 관리자 심사 상태. verifyStatus(NTS 진위확인 결과)와는 별개 개념 - NTS 일치는
     * 등록 가능 조건일 뿐, 실제 승인은 관리자가 첨부서류를 보고 결정한다.
     * REVOKED: 승인 후 조작 서류 등으로 밝혀져 관리자가 취소 처리한 경우.
     */
    public enum ApprovalStatus {
        PENDING_REVIEW, APPROVED, REJECTED, REVOKED
    }

}
