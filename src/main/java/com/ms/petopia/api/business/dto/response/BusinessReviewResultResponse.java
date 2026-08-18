package com.ms.petopia.api.business.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// PATCH /api/businesses/{id}/approve|reject|revoke 공통 응답
@Getter
@Builder
public class BusinessReviewResultResponse {

    private Long businessId;
    private String approvalStatus;
    private String rejectReason; // approve 응답에서는 null
    private LocalDateTime reviewedAt;

}
