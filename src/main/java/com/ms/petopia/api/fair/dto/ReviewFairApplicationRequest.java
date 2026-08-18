package com.ms.petopia.api.fair.dto;

/**
 * 행사 신청 검토(승인/반려) 요청.
 *
 * <p>{@code rejectReason}은 {@code decision}이 REJECT일 때만 필수다.
 * {@code openingFeeAmount}는 {@code decision}이 APPROVE일 때만 필수다(개설비 금액).
 * {@code paymentDueDays}는 APPROVE일 때만 의미가 있고 선택 입력이다 - 비워두면
 * {@code FairService.DEFAULT_PAYMENT_DUE_DAYS}(기본 7일)를 쓴다. 입력하면 1 이상이어야 한다.
 */
public record ReviewFairApplicationRequest(
        FairReviewDecision decision,
        Long openingFeeAmount,
        String rejectReason,
        Integer paymentDueDays
) {
}
