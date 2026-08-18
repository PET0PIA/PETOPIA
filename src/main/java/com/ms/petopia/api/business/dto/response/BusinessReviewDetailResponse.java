package com.ms.petopia.api.business.dto.response;

import com.ms.petopia.api.business.domain.Business;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// GET /api/businesses/{id}/review 응답 - 소유자 체크 없는 관리자용 상세
@Getter
@Builder
public class BusinessReviewDetailResponse {

    private Long businessId;
    private String name;
    private String ceoName;
    private String bizRegNo;
    private LocalDate startDate;
    private String address;
    private String phone;
    private String website;
    private String verifyStatus;
    private String approvalStatus;
    private String documentUrl; // 첨부된 사업자등록증 공개 URL (nullable - 구버전 자동승인 행은 첨부 없음)
    private String rejectReason;
    private Long reviewedBy; // null이면 구버전 자동승인
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    // Business 도메인 객체 + 이미 확정된 documentUrl을 받아 응답용으로 조립
    public static BusinessReviewDetailResponse from(Business business, String documentUrl) {

        return BusinessReviewDetailResponse.builder()
                .businessId(business.getBusinessId())
                .name(business.getName())
                .ceoName(business.getCeoName())
                .bizRegNo(business.getBizRegNo())
                .startDate(business.getStartDate())
                .address(business.getAddress())
                .phone(business.getPhone())
                .website(business.getWebsite())
                .verifyStatus(business.getVerifyStatus().name())
                .approvalStatus(business.getApprovalStatus().name())
                .documentUrl(documentUrl)
                .rejectReason(business.getRejectReason())
                .reviewedBy(business.getReviewedBy())
                .reviewedAt(business.getReviewedAt())
                .createdAt(business.getCreatedAt())
                .build();

    }

}
