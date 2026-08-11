package com.ms.petopia.api.fair.dto;

/**
 * 행사 신청 검토(승인/반려) 요청.
 *
 * <p>{@code rejectReason}은 {@code decision}이 REJECT일 때만 필수다.
 * {@code openingFeeAmount}는 {@code decision}이 APPROVE일 때만 필수다(개설비 금액).
 */
public record ReviewFairApplicationRequest(
        FairReviewDecision decision,
        Long openingFeeAmount,
        String rejectReason
) {
}
