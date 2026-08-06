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

    // 신청 취소 시
    private String cancelRequestStatus; // 최신 취소 요청 상태(REQUESTED/APPROVED/REJECTED), 요청한 적 없으면 null

}
