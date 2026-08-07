package com.ms.petopia.api.application.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCancelRequest {

    private Long cancelRequestId;
    private Long applicationId;
    private String reason;
    private Status status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;

    /*
     * 취소 요청 처리 상태.
     * REQUESTED -> APPROVED(담당자 승인, application도 CANCELED로 전환)
     * REQUESTED -> REJECTED(담당자 거부, application 상태는 그대로 유지)
     */
    public enum Status {
        REQUESTED, APPROVED, REJECTED
    }

}
