package com.ms.petopia.api.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationReviewSummaryResponse {

    private Long applicationId;
    private Long businessId;
    private String businessName; // business.name
    private String status; // 신청 처리 상태
    private LocalDateTime submittedAt; // 신청 날짜
    private Long finalPrice; // 승인 전이면 null
    private String rejectReason; // 반려 아니면 null

}
