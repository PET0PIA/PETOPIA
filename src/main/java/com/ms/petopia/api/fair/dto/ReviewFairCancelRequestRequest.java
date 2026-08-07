package com.ms.petopia.api.fair.dto;

/**
 * 행사 취소 신청 검토(승인/반려) 요청. 행사 신청 검토와 결정 종류가 같아
 * {@link FairReviewDecision}을 그대로 재사용한다.
 *
 * <p>{@code rejectReason}은 {@code decision}이 REJECT일 때만 필수다.
 */
public record ReviewFairCancelRequestRequest(
        FairReviewDecision decision,
        String rejectReason
) {
}
