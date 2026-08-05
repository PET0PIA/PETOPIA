package com.ms.petopia.api.application.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    private Long applicationId;
    private Long businessId;
    private Long fairId;
    private Status status; // 신청 처리 상태
    private String rejectReason; // 반려 사유
    private Long finalPrice; // 승인 시 확정 총 참가비
    private LocalDateTime submittedAt; // 제출일시
    private LocalDateTime reviewedAt; // 심사일시
    private LocalDateTime paymentDueAt; // 결제 기한

    /*
     * 신청 처리 상태.
     * PENDING_REVIEW -> PAYMENT_PENDING(승인) -> CONFIRMED(결제완료)
     * PENDING_REVIEW -> REJECTED(반려, 재신청 가능)
     * PAYMENT_PENDING/CONFIRMED -> CANCELED(취소 승인)
     */
    public enum Status {
        PENDING_REVIEW, REJECTED, PAYMENT_PENDING, CONFIRMED, CANCELED
    }

}
