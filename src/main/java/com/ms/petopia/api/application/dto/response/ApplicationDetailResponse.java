package com.ms.petopia.api.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

// GET /api/applications/{applicationId} 응답
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDetailResponse {

    private Long applicationId;
    private Long fairId;
    private Long businessId;
    private String status; // 신청 처리 상태
    private String purpose; // 참가 목적
    private String itemsDesc; // 판매·전시 품목
    private String managerName; // 신청 담당자명
    private String managerPhone; // 신청 담당자 연락처
    private String managerEmail; // 신청 담당자 이메일
    private Long finalPrice; // 승인 전이면 null
    private String rejectReason; // 반려 아니면 null
    private String attachmentUrl; // 제출서류 zip URL, 없으면 null
    private List<ApplicationSlotDetailResponse> slots;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt; // 심사 전이면 null

}
