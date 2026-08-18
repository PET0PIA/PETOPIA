package com.ms.petopia.api.review.dto;

/**
 * 리뷰 신고 요청.
 *
 * @param reason       SPAM/ABUSE/FALSE_INFO/ETC 중 하나. 문자열로 받아서 서비스 계층에서
 *                      {@link FairReviewReportReason}으로 검증한다(잘못된 값이면 REVIEW_INVALID_REPORT_REASON).
 * @param reasonDetail reason이 ETC일 때만 필요한 상세 사유.
 */
public record CreateFairReviewReportRequest(
        String reason,
        String reasonDetail
) {
}
