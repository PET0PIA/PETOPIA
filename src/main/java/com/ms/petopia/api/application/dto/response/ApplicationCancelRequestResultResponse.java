package com.ms.petopia.api.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;

// PUT /api/applications/{applicationId}/cancel-requests/approve, /reject 공용 응답
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCancelRequestResultResponse {

    private Long cancelRequestId;
    private Long applicationId;
    private String status; // 취소 요청 처리 상태: APPROVED 또는 REJECTED
    private String applicationStatus; // 처리 후 신청 상태 (승인 시 CANCELED, 반려 시 기존 상태 유지)
    private LocalDateTime decidedAt;
    private Boolean boothDeleted; // 승인 시 이전 상태가 CONFIRMED였다면 true(부스 삭제됨), 아니면 false

}
