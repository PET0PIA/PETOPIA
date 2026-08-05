package com.ms.petopia.api.application.dto.response;

import lombok.*;

// GET /api/applications 응답 (목록 항목 하나)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationSummaryResponse {

    private Long applicationId;
    private String fairName; // fairs.name
    private String status; // 신청 처리 상태
    private Long finalPrice; // 승인 전이면 null
    private String rejectReason; // 반려 아니면 null

}
