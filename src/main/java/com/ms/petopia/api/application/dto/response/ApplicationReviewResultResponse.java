package com.ms.petopia.api.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;

// PUT /api/applications/{applicationId}/approve, /reject 공용 응답
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationReviewResultResponse {

    private Long applicationId;
    private String status; // PAYMENT_PENDING(승인) 또는 REJECTED(반려)
    private Long finalPrice; // 승인 시에만 채워짐
    private LocalDateTime paymentDueAt; // 승인 시에만 채워짐
    private String rejectReason; // 반려 시에만 채워짐
    private LocalDateTime reviewedAt;

}
