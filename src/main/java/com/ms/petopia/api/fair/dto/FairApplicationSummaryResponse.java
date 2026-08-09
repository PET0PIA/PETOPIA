package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 마이페이지 "내 신청 현황" 목록 한 줄. 상세 화면으로 넘어가기 전 훑어보는 용도라
 * {@link FairApplicationDetailResponse}보다 필드를 줄였다(managerPhone/managerEmail 등
 * PII는 목록에 넣지 않는다 - 상세 조회에서만 노출).
 *
 * @param canceledAt 취소 승인 일시(취소 아니면 null). 취소 승인({@code FairCancelRequestService#review})은
 *                   fairs.canceled_at만 채우고 status는 그대로 두므로, 취소 여부는 status가 아니라
 *                   이 필드로 판단해야 한다 - 그렇지 않으면 취소된 행사가 화면에 계속
 *                   PREPARING/IN_PROGRESS 등으로 표시된다.
 */
public record FairApplicationSummaryResponse(
        Long fairId,
        String name,
        String status,
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime canceledAt
) {
}
