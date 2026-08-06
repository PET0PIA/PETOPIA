package com.ms.petopia.api.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;

// GET /api/fairs/{fairId}/cancel-requests 응답 (목록 항목 하나, 행사 담당자용)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCancelRequestSummaryResponse {

    private Long cancelRequestId;
    private Long applicationId;
    private Long businessId;
    private String businessName; // business.name
    private String reason; // 취소 사유
    private String status; // REQUESTED / APPROVED / REJECTED
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt; // 아직 처리 안 됐으면 null

}
