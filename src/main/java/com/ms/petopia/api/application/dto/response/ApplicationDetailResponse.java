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

    // 신청 취소 시
    private String cancelRequestStatus; // 최신 취소 요청 상태(REQUESTED/APPROVED/REJECTED), 요청한 적 없으면 null
    private String cancelReason; // 취소 요청 사유, 없으면 null
    private LocalDateTime cancelDecidedAt; // 담당자가 취소 요청 처리한 시각, 미처리/없으면 null
    private Boolean cancelable; // 취소 요청 가능 여부 (프론트 버튼 활성화 판단용)

}
