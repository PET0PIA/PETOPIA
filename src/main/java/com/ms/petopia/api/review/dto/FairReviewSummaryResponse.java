package com.ms.petopia.api.review.dto;

/**
 * @param averageRating 리뷰가 하나도 없으면 null.
 * @param reviewCount   리뷰 총 개수.
 */
public record FairReviewSummaryResponse(
        Long fairId,
        Double averageRating,
        long reviewCount
) {
    public static FairReviewSummaryResponse from(Long fairId, FairReviewSummaryRow row) {
        return new FairReviewSummaryResponse(fairId, row.getAverageRating(), row.getReviewCount());
    }
}
