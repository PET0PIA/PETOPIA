package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 마이페이지 "내 신청 현황" 목록 한 줄. 상세 화면으로 넘어가기 전 훑어보는 용도라
 * {@link FairApplicationDetailResponse}보다 필드를 줄였다(managerPhone/managerEmail 등
 * PII는 목록에 넣지 않는다 - 상세 조회에서만 노출).
 */
public record FairApplicationSummaryResponse(
        Long fairId,
        String name,
        String status,
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}
